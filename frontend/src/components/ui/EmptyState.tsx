// Estado vacío reutilizable: ícono + título + pista opcional + acción opcional.
import { Icon, type IconName } from "./Icon";

export function EmptyState({
  icon,
  title,
  hint,
  action,
}: {
  icon: IconName;
  title: string;
  hint?: string;
  action?: React.ReactNode;
}) {
  return (
    <div className="empty">
      <span className="empty__icon">
        <Icon name={icon} size={30} />
      </span>
      <p className="empty__title">{title}</p>
      {hint && <p className="muted">{hint}</p>}
      {action && <div className="empty__action">{action}</div>}
    </div>
  );
}
