import { HeaderIconButton } from '../../../shared/components/AppHeader';

/** Top-bar notifications entry; the unread count is part of its accessible name. `current` while the Notifications page is open. */
export function NotificationBell({ unread, onClick, href, current }: { unread: number; onClick?: () => void; href?: string; current?: boolean }) {
    return <HeaderIconButton label="Notifications" icon="bell" badge={unread} onClick={onClick} href={href} current={current} />;
}
