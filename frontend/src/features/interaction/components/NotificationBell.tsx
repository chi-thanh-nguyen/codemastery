import { HeaderIconButton } from '../../../shared/components/AppHeader';
import { useEffect } from 'react';
import { useNotifications } from '../hooks/useNotifications';
/** Top-bar notifications entry; the unread count is part of its accessible name. `current` while the Notifications page is open. */
export function NotificationBell({ unread, onClick, href, current }: { unread: number; onClick?: () => void; href?: string; current?: boolean }) {
    return <HeaderIconButton label="Notifications" icon="bell" badge={unread} onClick={onClick} href={href} current={current} />;
}

export function LiveNotificationBell({ href, current }: { href?: string; current?: boolean }) {
    const { unreadCount, refreshUnreadCount } = useNotifications();

    // Re-read unread count on mount / focus
    useEffect(() => {
        refreshUnreadCount();
    }, [refreshUnreadCount]);

    return <NotificationBell unread={unreadCount} href={href} current={current} />;
}