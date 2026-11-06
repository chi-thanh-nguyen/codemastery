import { HeaderIconButton } from '../../../shared/components/AppHeader';

/** Top-bar notifications entry; the unread count is part of its accessible name. */
export function NotificationBell({ unread, onClick, href }: { unread: number; onClick?: () => void; href?: string }) {
    return <HeaderIconButton label="Notifications" icon="bell" badge={unread} onClick={onClick} href={href} />;
}
