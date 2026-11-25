package com.codemastery.modules.interaction.api.controller;

import com.codemastery.common.api.PageResponse;
import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.modules.auth.application.service.UserAccountService;
import com.codemastery.modules.interaction.api.dto.InteractionResponses.MarkedCountResponse;
import com.codemastery.modules.interaction.api.dto.InteractionResponses.NotificationResponse;
import com.codemastery.modules.interaction.api.dto.InteractionResponses.UnreadCountResponse;
import com.codemastery.modules.interaction.application.service.NotificationService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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
            @RequestParam MultiValueMap<String, String> params) {
        UUID userId = currentUserId();
        String unreadOnly = singleValue(params, "unreadOnly");
        if (unreadOnly != null && !unreadOnly.equals("true") && !unreadOnly.equals("false")) {
            throw new BusinessRuleException("Invalid unreadOnly");
        }
        int page = pageValue(params, "page", PageResponse.DEFAULT_PAGE);
        int size = pageValue(params, "size", PageResponse.DEFAULT_SIZE);
        var result = notifications.listOwn(userId, "true".equals(unreadOnly), page, size);
        return noStore(PageResponse.from(result.map(NotificationResponse::from)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> unreadCount() {
        return noStore(new UnreadCountResponse(notifications.unreadCount(currentUserId())));
    }

    @PutMapping("/{notificationId}/read")
    public ResponseEntity<NotificationResponse> markRead(@PathVariable("notificationId") String notificationId) {
        UUID userId = currentUserId();
        UUID id;
        try {
            id = UUID.fromString(notificationId);
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Invalid notification identifier");
        }
        if (!id.toString().equalsIgnoreCase(notificationId)) {
            throw new BusinessRuleException("Invalid notification identifier");
        }
        return noStore(NotificationResponse.from(notifications.markRead(userId, id)));
    }

    @PutMapping("/read-all")
    public ResponseEntity<MarkedCountResponse> markAllRead() {
        return noStore(new MarkedCountResponse(notifications.markAllRead(currentUserId())));
    }

    private static int pageValue(MultiValueMap<String, String> params, String name, int defaultValue) {
        String value = singleValue(params, name);
        if (value == null) {
            return defaultValue;
        }
        if (value.matches("[0-9]+")) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ex) {
                throw new BusinessRuleException("Invalid page or size");
            }
        }
        throw new BusinessRuleException("Invalid page or size");
    }

    private static String singleValue(MultiValueMap<String, String> params, String name) {
        List<String> values = params.get(name);
        if (values == null) {
            return null;
        }
        if (values.size() != 1) {
            throw new BusinessRuleException("Invalid notification query");
        }
        return values.getFirst();
    }

    private UUID currentUserId() {
        return accounts.requireCurrentIdentity().userId();
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
