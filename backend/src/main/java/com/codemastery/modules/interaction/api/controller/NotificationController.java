package com.codemastery.modules.interaction.api.controller;

import com.codemastery.common.api.PageResponse;
import com.codemastery.modules.auth.application.service.UserAccountService;
import com.codemastery.modules.interaction.api.dto.InteractionResponses.MarkedCountResponse;
import com.codemastery.modules.interaction.api.dto.InteractionResponses.NotificationResponse;
import com.codemastery.modules.interaction.api.dto.InteractionResponses.UnreadCountResponse;
import com.codemastery.modules.interaction.application.service.NotificationService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

/**
 * CT11.read (ADR012 §4.2): the signed-in account's own notifications. The identity comes only
 * from the approved CT01.identity facade, never from a request parameter, so a user can reach
 * only their own rows. Mutations take no body; a body is ignored.
 */
@RestController
@RequestMapping("/api/me/notifications")
public class NotificationController {

    private final NotificationService notifications;
    private final UserAccountService accounts;

    public NotificationController(NotificationService notifications, UserAccountService accounts) {
        this.notifications = Objects.requireNonNull(notifications);
        this.accounts = Objects.requireNonNull(accounts);
    }

    @GetMapping
    public ResponseEntity<PageResponse<NotificationResponse>> list(
            @RequestParam(name = "unreadOnly", defaultValue = "false") boolean unreadOnly,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        var result = notifications.listOwn(currentUserId(), unreadOnly, page, size);
        return noStore(PageResponse.from(result.map(NotificationResponse::from)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> unreadCount() {
        return noStore(new UnreadCountResponse(notifications.unreadCount(currentUserId())));
    }

    @PutMapping("/{notificationId}/read")
    public ResponseEntity<NotificationResponse> markRead(@PathVariable("notificationId") UUID notificationId) {
        return noStore(NotificationResponse.from(notifications.markRead(currentUserId(), notificationId)));
    }

    @PutMapping("/read-all")
    public ResponseEntity<MarkedCountResponse> markAllRead() {
        return noStore(new MarkedCountResponse(notifications.markAllRead(currentUserId())));
    }

    private UUID currentUserId() {
        return accounts.requireCurrentIdentity().userId();
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
