import { Button, Tooltip } from 'antd';

interface Props {
  onClick: () => void;
  disabled?: boolean;
}

export function StopMonitorButton({ onClick, disabled }: Props) {
  return (
    <Tooltip title="停止">
      <Button
        type="primary"
        danger
        icon={<span className="dx-stop-btn__icon" aria-hidden="true" />}
        className="dx-stop-btn"
        onClick={onClick}
        disabled={disabled}
      >
        停止
      </Button>
    </Tooltip>
  );
}
