'use client';

import { useEffect, useRef, useState, type ReactNode } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import AppBar from '@mui/material/AppBar';
import Box from '@mui/material/Box';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';
import IconButton from '@mui/material/IconButton';
import Drawer from '@mui/material/Drawer';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import BottomNavigation from '@mui/material/BottomNavigation';
import BottomNavigationAction from '@mui/material/BottomNavigationAction';
import Badge from '@mui/material/Badge';
import Avatar from '@mui/material/Avatar';
import Divider from '@mui/material/Divider';
import Chip from '@mui/material/Chip';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useTheme } from '@mui/material/styles';
import MenuIcon from '@mui/icons-material/Menu';
import HomeIcon from '@mui/icons-material/Home';
import MeetingRoomIcon from '@mui/icons-material/MeetingRoom';
import BarChartIcon from '@mui/icons-material/BarChart';
import ScheduleIcon from '@mui/icons-material/Schedule';
import SettingsIcon from '@mui/icons-material/Settings';
import DeviceThermostatIcon from '@mui/icons-material/DeviceThermostat';
import NotificationsIcon from '@mui/icons-material/Notifications';
import CloseIcon from '@mui/icons-material/Close';
import WarningIcon from '@mui/icons-material/Warning';
import ErrorIcon from '@mui/icons-material/Error';
import InfoIcon from '@mui/icons-material/Info';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ThermostatIcon from '@mui/icons-material/Thermostat';
import WifiIcon from '@mui/icons-material/Wifi';
import WifiOffIcon from '@mui/icons-material/WifiOff';
import Popover from '@mui/material/Popover';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import ThemeToggle from './ThemeToggle';
import { useUiStore } from '@/stores/uiStore';
import { useSensorStore } from '@/stores/sensorStore';
import { apiClient } from '@/lib/api';
import type { Alert } from '@/types';

const severityIcons: Record<string, typeof InfoIcon> = {
  critical: ErrorIcon, warning: WarningIcon, info: InfoIcon, success: CheckCircleIcon,
};
const severityColors: Record<string, string> = {
  critical: '#F44336', warning: '#FF9800', info: '#2196F3', success: '#4CAF50',
};

const DRAWER_WIDTH = 260;

interface NavItem {
  label: string;
  path: string;
  icon: typeof HomeIcon;
}

const navItems: NavItem[] = [
  { label: 'Ana Sayfa', path: '/', icon: HomeIcon },
  { label: 'Odalar', path: '/rooms', icon: MeetingRoomIcon },
  { label: 'Cihazlar', path: '/devices', icon: DeviceThermostatIcon },
  { label: 'İstatistikler', path: '/analytics', icon: BarChartIcon },
  { label: 'Program', path: '/schedule', icon: ScheduleIcon },
  { label: 'Ayarlar', path: '/settings', icon: SettingsIcon },
];

const bottomNavItems = [navItems[0], navItems[1], navItems[3], navItems[5]];

interface Props {
  children: ReactNode;
}

export default function AppLayout({ children }: Props) {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const pathname = usePathname();
  const router = useRouter();
  const sidebarOpen = useUiStore((s) => s.sidebarOpen);
  const toggleSidebar = useUiStore((s) => s.toggleSidebar);
  const setSidebarOpen = useUiStore((s) => s.setSidebarOpen);
  const connectionStatus = useSensorStore((s) => s.connectionStatus);
  const alerts = useUiStore((s) => s.alerts);
  const dismissedAlerts = useUiStore((s) => s.dismissedAlerts);
  const setAlerts = useUiStore((s) => s.setAlerts);
  const dismissAlert = useUiStore((s) => s.dismissAlert);

  const bellRef = useRef<HTMLButtonElement>(null);
  const [bellOpen, setBellOpen] = useState(false);

  // Load alerts on mount + every 5 min
  useEffect(() => {
    const load = () => {
      apiClient.getAlerts()
        .then((r) => {
          const list = (r.alerts || []).map((a: Alert, i: number) => ({
            ...a,
            id: a.id || `${a.type}-${a.severity}-${i}`,
          }));
          setAlerts(list);
        })
        .catch(() => {});
    };
    load();
    const iv = setInterval(load, 5 * 60 * 1000);
    return () => clearInterval(iv);
  }, [setAlerts]);

  const visibleAlerts = alerts.filter((a) => !dismissedAlerts.includes(a.id));

  const activeIndex = bottomNavItems.findIndex((item) => item.path === pathname);

  const drawerContent = (
    <Box sx={{ width: DRAWER_WIDTH }}>
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1.5,
          p: 2.5,
          pb: 1.5,
        }}
      >
        <ThermostatIcon sx={{ fontSize: 32, color: 'primary.main' }} />
        <Box>
          <Typography variant="h6" sx={{ fontWeight: 700, lineHeight: 1.2 }}>
            TonbilTerm
          </Typography>
          <Typography variant="caption" color="text.secondary">
            Akilli Termostat
          </Typography>
        </Box>
      </Box>
      <Box sx={{ px: 2.5, pb: 2 }}>
        <Chip
          icon={connectionStatus === 'connected' ? <WifiIcon /> : <WifiOffIcon />}
          label={connectionStatus === 'connected' ? 'Bagli' : 'Baglaniyor...'}
          size="small"
          color={connectionStatus === 'connected' ? 'success' : 'warning'}
          variant="outlined"
          sx={{ width: '100%' }}
        />
      </Box>
      <Divider />
      <List sx={{ px: 1, pt: 1 }}>
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive = pathname === item.path;
          return (
            <ListItemButton
              key={item.path}
              selected={isActive}
              onClick={() => {
                router.push(item.path);
                if (isMobile) setSidebarOpen(false);
              }}
              sx={{
                borderRadius: 3,
                mb: 0.5,
                '&.Mui-selected': {
                  backgroundColor: 'primary.main',
                  color: 'primary.contrastText',
                  '& .MuiListItemIcon-root': { color: 'primary.contrastText' },
                  '&:hover': { backgroundColor: 'primary.dark' },
                },
              }}
            >
              <ListItemIcon sx={{ minWidth: 40 }}>
                <Icon />
              </ListItemIcon>
              <ListItemText primary={item.label} />
            </ListItemButton>
          );
        })}
      </List>
    </Box>
  );

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      {/* Desktop persistent drawer */}
      {!isMobile && (
        <Drawer
          variant="persistent"
          open={sidebarOpen}
          sx={{
            width: sidebarOpen ? DRAWER_WIDTH : 0,
            flexShrink: 0,
            transition: 'width 0.3s ease',
            '& .MuiDrawer-paper': {
              width: DRAWER_WIDTH,
              boxSizing: 'border-box',
              borderRight: '1px solid',
              borderColor: 'divider',
            },
          }}
        >
          {drawerContent}
        </Drawer>
      )}

      {/* Mobile temporary drawer */}
      {isMobile && (
        <Drawer
          variant="temporary"
          open={sidebarOpen}
          onClose={() => setSidebarOpen(false)}
          ModalProps={{ keepMounted: true }}
          sx={{
            '& .MuiDrawer-paper': {
              width: DRAWER_WIDTH,
              boxSizing: 'border-box',
            },
          }}
        >
          {drawerContent}
        </Drawer>
      )}

      {/* Main content area */}
      <Box
        sx={{
          flexGrow: 1,
          display: 'flex',
          flexDirection: 'column',
          minHeight: '100vh',
          transition: 'margin 0.3s ease',
        }}
      >
        <AppBar
          position="sticky"
          elevation={0}
          sx={{
            backgroundColor: 'background.paper',
            color: 'text.primary',
            borderBottom: '1px solid',
            borderColor: 'divider',
          }}
        >
          <Toolbar>
            <IconButton
              edge="start"
              color="inherit"
              onClick={toggleSidebar}
              aria-label="Menu"
              sx={{ mr: 1 }}
            >
              <MenuIcon />
            </IconButton>

            {isMobile && (
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                <ThermostatIcon sx={{ color: 'primary.main' }} />
                <Typography variant="h6" sx={{ fontWeight: 700 }}>
                  TonbilTerm
                </Typography>
              </Box>
            )}

            <Box sx={{ flexGrow: 1 }} />

            <ThemeToggle />

            <IconButton
              ref={bellRef}
              color="inherit"
              aria-label="Bildirimler"
              onClick={() => setBellOpen((v) => !v)}
            >
              <Badge badgeContent={visibleAlerts.length} color="error">
                <NotificationsIcon />
              </Badge>
            </IconButton>

            <Popover
              open={bellOpen}
              anchorEl={bellRef.current}
              onClose={() => setBellOpen(false)}
              anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
              transformOrigin={{ vertical: 'top', horizontal: 'right' }}
              slotProps={{ paper: { sx: { width: 360, maxHeight: 420, overflow: 'auto', borderRadius: 3, mt: 0.5 } } }}
            >
              <Box sx={{ p: 2, borderBottom: '1px solid', borderColor: 'divider', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <Typography variant="subtitle1" fontWeight={700}>Bildirimler</Typography>
                <Chip label={`${visibleAlerts.length}`} size="small" color={visibleAlerts.length > 0 ? 'error' : 'default'} />
              </Box>
              {visibleAlerts.length === 0 ? (
                <Box sx={{ p: 3, textAlign: 'center' }}>
                  <NotificationsIcon sx={{ fontSize: 40, color: 'text.disabled', mb: 1 }} />
                  <Typography variant="body2" color="text.secondary">Bildirim yok</Typography>
                </Box>
              ) : (
                <Box sx={{ display: 'flex', flexDirection: 'column' }}>
                  {visibleAlerts.map((alert) => {
                    const AlertIcon = severityIcons[alert.severity] || InfoIcon;
                    const color = severityColors[alert.severity] || '#2196F3';
                    return (
                      <Card key={alert.id} sx={{ m: 1, borderLeft: `3px solid ${color}`, bgcolor: `${color}08` }} elevation={0}>
                        <CardContent sx={{ py: 1, px: 1.5, '&:last-child': { pb: 1 }, display: 'flex', alignItems: 'flex-start', gap: 1 }}>
                          <AlertIcon sx={{ color, fontSize: 20, mt: 0.25 }} />
                          <Box sx={{ flex: 1, minWidth: 0 }}>
                            <Typography variant="caption" fontWeight={700} display="block">{alert.title}</Typography>
                            <Typography variant="caption" color="text.secondary" display="block">{alert.message}</Typography>
                          </Box>
                          <IconButton size="small" onClick={() => dismissAlert(alert.id)} sx={{ opacity: 0.5, '&:hover': { opacity: 1 } }}>
                            <CloseIcon sx={{ fontSize: 14 }} />
                          </IconButton>
                        </CardContent>
                      </Card>
                    );
                  })}
                </Box>
              )}
            </Popover>

            <IconButton sx={{ ml: 0.5 }} aria-label="Profil">
              <Avatar
                sx={{
                  width: 32,
                  height: 32,
                  bgcolor: 'primary.main',
                  fontSize: '0.875rem',
                }}
              >
                T
              </Avatar>
            </IconButton>
          </Toolbar>
        </AppBar>

        <Box
          component="main"
          sx={{
            flexGrow: 1,
            p: { xs: 2, sm: 3 },
            pb: { xs: 10, md: 3 },
            maxWidth: 1200,
            width: '100%',
            mx: 'auto',
          }}
        >
          {children}
        </Box>

        {/* Mobile bottom navigation */}
        {isMobile && (
          <BottomNavigation
            value={activeIndex >= 0 ? activeIndex : 0}
            onChange={(_, newValue) => {
              router.push(bottomNavItems[newValue].path);
            }}
            showLabels
            sx={{
              position: 'fixed',
              bottom: 0,
              left: 0,
              right: 0,
              borderTop: '1px solid',
              borderColor: 'divider',
              backgroundColor: 'background.paper',
              zIndex: 1100,
              height: 64,
            }}
          >
            {bottomNavItems.map((item) => {
              const Icon = item.icon;
              return (
                <BottomNavigationAction
                  key={item.path}
                  label={item.label.replace('İstatistikler', 'İstat').replace('Ana Sayfa', 'Ana')}
                  icon={<Icon />}
                />
              );
            })}
          </BottomNavigation>
        )}
      </Box>
    </Box>
  );
}
