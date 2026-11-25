package com.sam.be.modules.chat.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.storage.service.StorageService;
import com.sam.be.common.storage.service.impl.CloudinaryStorageServiceImpl;
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

    @Test
    void rejectsSigningDocumentsOutsideTheAuthorizedRoom() {
        Cloudinary cloudinary = mock(Cloudinary.class);
        var documents = new CloudinaryStorageServiceImpl(cloudinary);
        org.springframework.test.util.ReflectionTestUtils.setField(documents, "cloudName", "test");
        String folder = "https://res.cloudinary.com/test/raw/upload/v123/chat/";
        String ownRoom = folder + room.getId() + "/";
        for (String url :
                List.of(
                        folder + UUID.randomUUID() + "/file.pdf",
                        ownRoom + "../file.pdf",
                        ownRoom + "file.txt",
                        ownRoom + "file.pdf?other=1")) {
            assertThrows(
                    ApiException.class, () -> documents.downloadChatDocument(url, room.getId(), 8));
        }
        verifyNoInteractions(cloudinary);
    }

    @Test
    void validatesVideoContainersAndRejectsDisguisedVideo() {
        byte[] mp4 = new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 0, 0};
        assertEquals("video/mp4", ChatFilePolicy.validate("demo.mp4", mp4));
        byte[] webm = new byte[] {0x1a, 0x45, (byte) 0xdf, (byte) 0xa3, 'w', 'e', 'b', 'm'};
        assertEquals("video/webm", ChatFilePolicy.validate("demo.webm", webm));
        assertThrows(
                ApiException.class,
                () -> ChatFilePolicy.validate("fake.mp4", "not video".getBytes()));
        assertThrows(
                ApiException.class,
                () ->
                        ChatFilePolicy.validate(
                                "large.webm", new byte[(int) ChatFilePolicy.VIDEO_LIMIT + 1]));
    }

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
    void uploadsChatVideoToCloudinaryAsVideoWithAnExtensionlessPublicId() throws Exception {
        setupRoom();
        var cloudinary = mock(Cloudinary.class);
        var uploader = mock(Uploader.class);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap()))
                .thenReturn(
                        Map.of(
                                "secure_url",
                                "https://res.cloudinary.com/cloud/video/upload/clip.mp4"));
        when(attachments.save(any())).thenAnswer(i -> i.getArgument(0));
        var chat =
                new ChatAttachmentService(
                        attachments, rooms, new CloudinaryStorageServiceImpl(cloudinary));
        byte[] mp4 = new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 0, 0};
        var result =
                chat.upload(
                        room.getId(),
                        clientId,
                        new MockMultipartFile("file", "clip.mp4", "video/mp4", mp4));

        assertEquals("video/mp4", result.contentType());
        assertEquals("clip.mp4", result.fileName());
        verify(uploader)
                .upload(
                        any(byte[].class),
                        argThat(
                                params ->
                                        "video".equals(params.get("resource_type"))
                                                && params.get("public_id")
                                                        .toString()
                                                        .startsWith("chat/" + room.getId() + "/")
                                                && params.get("public_id")
                                                        .toString()
                                                        .endsWith("_clip")));
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
                    attachment(clientId, room.getId(), ChatFilePolicy.VIDEO_LIMIT);
            attachment.setContentType("video/mp4");
            attachment.setFileName("demo.mp4");
            ids.add(attachment.getId());
            when(attachments.findForUpdate(attachment.getId())).thenReturn(Optional.of(attachment));
        }
        assertThrows(ApiException.class, () -> service.claim(room.getId(), clientId, ids));
        assertThrows(
                ApiException.class, () -> service.claim(room.getId(), clientId, ids.subList(0, 3)));
        assertEquals(2, service.claim(room.getId(), clientId, ids.subList(0, 2)).size());
    }
}
