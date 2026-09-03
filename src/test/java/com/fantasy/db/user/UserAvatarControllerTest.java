package com.fantasy.db.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserAvatarController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserAvatarControllerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
    private static final String PNG_BASE64 = Base64.getEncoder().encodeToString(PNG);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserAvatarService userAvatarService;

    @Test
    void returnsThePictureAsBase64WithItsType() throws Exception {
        when(userAvatarService.find(USER_ID)).thenReturn(UserAvatar.of(USER_ID, "image/png", PNG));

        mockMvc.perform(get("/api/v1/users/{userId}/avatar", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.data").value(PNG_BASE64))
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void returns404WhenThereIsNoPicture() throws Exception {
        when(userAvatarService.find(USER_ID)).thenThrow(new NoSuchElementException("none"));

        mockMvc.perform(get("/api/v1/users/{userId}/avatar", USER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void setsThePicture() throws Exception {
        when(userAvatarService.set(eq(USER_ID), eq("image/png"), any()))
                .thenReturn(UserAvatar.of(USER_ID, "image/png", PNG));

        mockMvc.perform(put("/api/v1/users/{userId}/avatar", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/png\",\"data\":\"" + PNG_BASE64 + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.data").value(PNG_BASE64));

        verify(userAvatarService).set(USER_ID, "image/png", PNG);
    }

    @Test
    void rejectsATypeThatIsNotAnImageWeServe() throws Exception {
        mockMvc.perform(put("/api/v1/users/{userId}/avatar", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/svg+xml\",\"data\":\"" + PNG_BASE64 + "\"}"))
                .andExpect(status().isBadRequest());

        verify(userAvatarService, never()).set(any(), any(), any());
    }

    @Test
    void rejectsAnEmptyPicture() throws Exception {
        mockMvc.perform(put("/api/v1/users/{userId}/avatar", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/png\",\"data\":\"\"}"))
                .andExpect(status().isBadRequest());

        verify(userAvatarService, never()).set(any(), any(), any());
    }

    @Test
    void rejectsAPictureLargerThanAllowed() throws Exception {
        String tooLarge = Base64.getEncoder().encodeToString(new byte[UserAvatar.MAX_BYTES + 1]);

        mockMvc.perform(put("/api/v1/users/{userId}/avatar", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/png\",\"data\":\"" + tooLarge + "\"}"))
                .andExpect(status().isBadRequest());

        verify(userAvatarService, never()).set(any(), any(), any());
    }

    @Test
    void returns404WhenSettingAPictureForAnUnknownAccount() throws Exception {
        when(userAvatarService.set(eq(USER_ID), eq("image/png"), any()))
                .thenThrow(new NoSuchElementException("no user"));

        mockMvc.perform(put("/api/v1/users/{userId}/avatar", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/png\",\"data\":\"" + PNG_BASE64 + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void removesThePicture() throws Exception {
        mockMvc.perform(delete("/api/v1/users/{userId}/avatar", USER_ID))
                .andExpect(status().isNoContent());

        verify(userAvatarService).delete(USER_ID);
    }

    @Test
    void returns404WhenRemovingAPictureThatIsNotThere() throws Exception {
        doThrow(new NoSuchElementException("none")).when(userAvatarService).delete(USER_ID);

        mockMvc.perform(delete("/api/v1/users/{userId}/avatar", USER_ID))
                .andExpect(status().isNotFound());
    }
}
