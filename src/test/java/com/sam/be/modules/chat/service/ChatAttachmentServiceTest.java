package com.sam.be.modules.chat.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sam.be.common.exception.ApiException;
import com.sam.be.common.storage.service.StorageService;
import com.sam.be.modules.chat.entity.*;
import com.sam.be.modules.chat.repository.*;
import com.sam.be.modules.job.entity.Job;
import com.sam.be.modules.user.entity.User;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ChatAttachmentServiceTest {
    private final ChatAttachmentRepository attachments = mock(ChatAttachmentRepository.class);
    private final ChatRoomRepository rooms = mock(ChatRoomRepository.class);
    private final StorageService storage = mock(StorageService.class);
    private final ChatAttachmentService service =
            new ChatAttachmentService(attachments, rooms, storage);
    private final UUID clientId = UUID.randomUUID();
    private final UUID freelancerId = UUID.randomUUID();
    private final ChatRoom room =
            ChatRoom.builder()
                    .id(UUID.randomUUID())
                    .client(User.builder().id(clientId).build())
                    .freelancer(User.builder().id(freelancerId).build())
                    .job(Job.builder().build())
                    .build();

    private void setupRoom() {
        when(rooms.findById(room.getId())).thenReturn(Optional.of(room));
    }

    private ChatAttachment attachment(UUID owner, UUID roomId, long size) {
        return ChatAttachment.builder()
                .id(UUID.randomUUID())
                .ownerId(owner)
                .room(ChatRoom.builder().id(roomId).build())
                .sizeBytes(size)
                .fileName("scope.pdf")
                .contentType("application/pdf")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void validatesActualImageAndRejectsDisguisedOrOversizedFiles() throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", out);
        assertEquals("image/png", ChatFilePolicy.validate("photo.png", out.toByteArray()));
        assertEquals(
                "application/pdf",
                ChatFilePolicy.validate(
                        "scope.pdf", "%PDF-1.7\nTest".getBytes(StandardCharsets.UTF_8)));
        assertEquals(
                "text/plain",
                ChatFilePolicy.validate(
                        "notes.txt", "Nội dung dự án".getBytes(StandardCharsets.UTF_8)));
        assertThrows(
                ApiException.class,
                () -> ChatFilePolicy.validate("photo.png", "<script>bad</script>".getBytes()));
        assertThrows(
                ApiException.class,
                () -> ChatFilePolicy.validate("photo.svg", "<svg/>".getBytes()));
        assertThrows(ApiException.class, () -> ChatFilePolicy.validate("run.exe", new byte[4]));
        assertThrows(
                ApiException.class,
                () ->
                        ChatFilePolicy.validate(
                                "photo.jpg", new byte[(int) ChatFilePolicy.IMAGE_LIMIT + 1]));
        assertThrows(
                ApiException.class,
                () ->
                        ChatFilePolicy.validate(
                                "scope.pdf", new byte[(int) ChatFilePolicy.FILE_LIMIT + 1]));
        assertEquals("scope.pdf", ChatFilePolicy.safeName("../../scope.pdf"));
    }

    @Test
    void outsidersCannotUploadBeforeCloudinaryIsCalled() {
        setupRoom();
        assertThrows(
                ApiException.class,
                () ->
                        service.upload(
                                room.getId(),
                                UUID.randomUUID(),
                                new MockMultipartFile(
                                        "file",
                                        "scope.pdf",
                                        "application/pdf",
                                        "%PDF-1.7".getBytes())));
        verifyNoInteractions(storage, attachments);
    }

    @Test
    void bothParticipantsCanUploadAndResponsesDoNotExposeStorageUrls() {
        setupRoom();
        when(storage.uploadFile(any(), anyString(), eq("chat/" + room.getId())))
                .thenReturn("https://res.cloudinary.com/cloud/raw/upload/private.pdf");
        when(attachments.save(any())).thenAnswer(i -> i.getArgument(0));
        var file =
                new MockMultipartFile(
                        "file", "scope.pdf", "application/pdf", "%PDF-1.7".getBytes());
        assertEquals("scope.pdf", service.upload(room.getId(), clientId, file).fileName());
        assertEquals(
                "application/pdf", service.upload(room.getId(), freelancerId, file).contentType());
        assertEquals(
                4,
                com.sam.be.modules.chat.dto.ChatAttachmentDto.class.getRecordComponents().length);
    }

    @Test
    void receiverCanReadOnlyAfterAttachmentIsSentAndNeverFromAnotherRoom() {
        setupRoom();
        ChatAttachment attachment = attachment(clientId, room.getId(), 100);
        when(attachments.findById(attachment.getId())).thenReturn(Optional.of(attachment));
        assertThrows(
                ApiException.class,
                () -> service.requireReadable(room.getId(), freelancerId, attachment.getId()));
        attachment.setMessage(ChatMessage.builder().id(UUID.randomUUID()).build());
        assertEquals(
                attachment,
                service.requireReadable(room.getId(), freelancerId, attachment.getId()));
        attachment.setRoom(ChatRoom.builder().id(UUID.randomUUID()).build());
        assertThrows(
                ApiException.class,
                () -> service.requireReadable(room.getId(), clientId, attachment.getId()));
    }

    @Test
    void claimsRejectOtherOwnersRoomsReusedUploadsExpiredUploadsAndDuplicateIds() {
        ChatAttachment attachment = attachment(clientId, room.getId(), 100);
        when(attachments.findForUpdate(attachment.getId())).thenReturn(Optional.of(attachment));
        assertThrows(
                ApiException.class,
                () -> service.claim(room.getId(), freelancerId, List.of(attachment.getId())));
        assertThrows(
                ApiException.class,
                () -> service.claim(UUID.randomUUID(), clientId, List.of(attachment.getId())));
        assertThrows(
                ApiException.class,
                () ->
                        service.claim(
                                room.getId(),
                                clientId,
                                List.of(attachment.getId(), attachment.getId())));
        assertThrows(
                ApiException.class,
                () -> service.claim(room.getId(), clientId, java.util.Arrays.asList((UUID) null)));
        attachment.setMessage(ChatMessage.builder().build());
        assertThrows(
                ApiException.class,
                () -> service.claim(room.getId(), clientId, List.of(attachment.getId())));
        attachment.setMessage(null);
        attachment.setCreatedAt(LocalDateTime.now().minusDays(2));
        assertThrows(
                ApiException.class,
                () -> service.claim(room.getId(), clientId, List.of(attachment.getId())));
    }

    @Test
    void perMessageCountAndAggregateSizeAreEnforced() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            ChatAttachment attachment =
                    attachment(clientId, room.getId(), ChatFilePolicy.FILE_LIMIT);
            ids.add(attachment.getId());
            when(attachments.findForUpdate(attachment.getId())).thenReturn(Optional.of(attachment));
        }
        assertThrows(ApiException.class, () -> service.claim(room.getId(), clientId, ids));
        assertThrows(
                ApiException.class, () -> service.claim(room.getId(), clientId, ids.subList(0, 3)));
        assertEquals(2, service.claim(room.getId(), clientId, ids.subList(0, 2)).size());
    }
}
