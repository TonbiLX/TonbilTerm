'use client';

import { useCallback, useRef } from 'react';
import Chip from '@mui/material/Chip';
import type { Profile } from '@/types';
import { IconByName } from './ProfileDialog';

// ---------------------------------------------------------------------------
// Long press hook
// ---------------------------------------------------------------------------

function useLongPress(callback: () => void, ms = 500) {
  const timerRef = useRef<ReturnType<typeof setTimeout>>();
  const triggeredRef = useRef(false);

  const start = useCallback(() => {
    triggeredRef.current = false;
    timerRef.current = setTimeout(() => {
      triggeredRef.current = true;
      callback();
    }, ms);
  }, [callback, ms]);

  const cancel = useCallback(() => {
    clearTimeout(timerRef.current);
  }, []);

  return {
    onTouchStart: start,
    onTouchEnd: cancel,
    onTouchMove: cancel,
    /** true if long press was triggered (suppress click) */
    wasTriggered: () => triggeredRef.current,
  };
}

// ---------------------------------------------------------------------------
// ProfileChip
// ---------------------------------------------------------------------------

interface ProfileChipProps {
  profile: Profile;
  isActive: boolean;
  disabled: boolean;
  onSelect: () => void;
  onEdit: () => void;
}

export default function ProfileChip({ profile, isActive, disabled, onSelect, onEdit }: ProfileChipProps) {
  const longPress = useLongPress(onEdit, 500);

  const handleClick = useCallback(() => {
    // Suppress click if long press was triggered
    if (longPress.wasTriggered()) return;
    onSelect();
  }, [onSelect, longPress]);

  const handleContextMenu = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    onEdit();
  }, [onEdit]);

  return (
    <Chip
      icon={<IconByName name={profile.icon} />}
      label={`${profile.name} (${profile.targetTemp}°)`}
      variant={isActive ? 'filled' : 'outlined'}
      color={isActive ? 'primary' : 'default'}
      onClick={handleClick}
      onContextMenu={handleContextMenu}
      onTouchStart={longPress.onTouchStart}
      onTouchEnd={longPress.onTouchEnd}
      onTouchMove={longPress.onTouchMove}
      disabled={disabled}
      sx={{
        px: 0.5,
        height: 36,
        fontSize: '0.8rem',
        fontWeight: isActive ? 700 : 500,
        transition: 'all 0.2s ease-in-out',
        ...(isActive && { boxShadow: '0 2px 8px rgba(139, 69, 19, 0.3)' }),
        // Prevent text selection on long press
        WebkitUserSelect: 'none',
        userSelect: 'none',
      }}
    />
  );
}
