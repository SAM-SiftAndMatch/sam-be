package com.sam.be.modules.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sam.be.infrastructure.cache.service.RedisCacheService;
import com.sam.be.modules.user.dto.response.ClientProfileResponse;
import com.sam.be.modules.user.dto.response.FreelancerProfileResponse;
import com.sam.be.modules.user.entity.FreelancerProfile;
import com.sam.be.modules.user.entity.User;
import com.sam.be.modules.user.repository.ClientProfileRepository;
import com.sam.be.modules.user.repository.FreelancerProfileRepository;
import com.sam.be.modules.user.repository.FreelancerSkillRepository;
import com.sam.be.modules.user.repository.UserRepository;
import com.sam.be.modules.user.service.impl.ProfileServiceImpl;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class ProfileServiceReadTest {

    @Test
    void freelancerProfileIsLoadedFromItsOwnRepository() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User user =
                User.builder().id(userId).fullName("Freelancer").email("dev@example.com").build();
        FreelancerProfile profile =
                FreelancerProfile.builder().id(profileId).headline("Java").build();
        UserRepository users = mock(UserRepository.class);
        FreelancerProfileRepository profiles = mock(FreelancerProfileRepository.class);
        FreelancerSkillRepository skills = mock(FreelancerSkillRepository.class);
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(profiles.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(skills.findAllByFreelancerId(userId)).thenReturn(List.of());

        FreelancerProfileResponse response =
                service(users, profiles, null, skills).getFreelancerProfile(userId);

        assertEquals(profileId, response.getId());
        assertEquals("Freelancer", response.getFullName());
        assertEquals("Java", response.getHeadline());
        assertTrue(response.getSkills().isEmpty());
        verify(profiles).findByUserId(userId);
    }

    @Test
    void missingClientProfileKeepsTheExistingEmptyResponse() {
        UUID userId = UUID.randomUUID();
        User user =
                User.builder().id(userId).fullName("Client").email("client@example.com").build();
        UserRepository users = mock(UserRepository.class);
        ClientProfileRepository profiles = mock(ClientProfileRepository.class);
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(profiles.findByUserId(userId)).thenReturn(Optional.empty());

        ClientProfileResponse response =
                service(users, null, profiles, null).getClientProfile(userId);

        assertEquals(userId, response.getUserId());
        assertEquals("Client", response.getFullName());
        assertNull(response.getId());
        assertNull(response.getCompanyName());
        verify(profiles).findByUserId(userId);
    }

    private ProfileServiceImpl service(
            UserRepository users,
            FreelancerProfileRepository freelancerProfiles,
            ClientProfileRepository clientProfiles,
            FreelancerSkillRepository skills) {
        RedisCacheService cache = mock(RedisCacheService.class);
        when(cache.getOrSet(any(), any(), eq(FreelancerProfileResponse.class), any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(3)).get());
        when(cache.getOrSet(any(), any(), eq(ClientProfileResponse.class), any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(3)).get());
        return new ProfileServiceImpl(
                users, freelancerProfiles, clientProfiles, skills, null, cache, null, null, null);
    }
}
