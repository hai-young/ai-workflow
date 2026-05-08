import type { ThemeConfig } from 'ant-design-vue'

const sharedToken = {
  colorPrimary: '#3B82F6',
  colorSuccess: '#22C55E',
  colorWarning: '#F59E0B',
  colorError: '#EF4444',
  colorInfo: '#3B82F6',
  fontFamily: "'SF Pro Display', -apple-system, sans-serif",
  fontFamilyCode: "'JetBrains Mono', 'Fira Code', monospace",
  fontSize: 14,
  fontSizeLG: 15,
  fontSizeXL: 18,
  borderRadius: 6,
  borderRadiusSM: 4,
  borderRadiusLG: 8,
  paddingXS: 4,
  paddingSM: 8,
  padding: 12,
  paddingMD: 16,
  paddingLG: 24,
  paddingXL: 32,
  motionDurationSlow: '0.3s',
  motionDurationMid: '0.2s',
  motionDurationFast: '0.1s',
} as const

export const darkTheme: ThemeConfig = {
  token: {
    ...sharedToken,
    colorBgBase: '#0B0E11',
    colorBgContainer: '#15191F',
    colorBgElevated: '#1C2128',
    colorBorder: '#252B33',
    colorTextBase: '#E8ECF1',
    colorTextSecondary: '#848E9C',
    colorTextTertiary: '#505968',
  },
  components: {
    Table: {
      headerBg: '#15191F',
      rowHoverBg: '#1C2128',
      borderColor: '#252B33',
    },
    Input: {
      activeBorderColor: '#3B82F6',
    },
    Button: {
      primaryShadow: '0 0 0 2px rgba(59, 130, 246, 0.3)',
    },
    Menu: {
      darkItemBg: 'transparent',
      darkItemSelectedBg: 'rgba(59, 130, 246, 0.15)',
    },
    Modal: {
      contentBg: '#1C2128',
      headerBg: '#1C2128',
      titleColor: '#E8ECF1',
    },
    Drawer: {
      colorBgElevated: '#15191F',
    },
    Select: {
      optionSelectedBg: 'rgba(59, 130, 246, 0.12)',
    },
  },
}

export const lightTheme: ThemeConfig = {
  token: {
    ...sharedToken,
    colorPrimary: '#2563EB',
    colorBgBase: '#F8F9FA',
    colorBgContainer: '#FFFFFF',
    colorBgElevated: '#FFFFFF',
    colorBorder: '#E9ECEF',
    colorTextBase: '#1A1D23',
    colorTextSecondary: '#6B7280',
    colorTextTertiary: '#9CA3AF',
  },
  components: {
    Table: {
      headerBg: '#F8F9FA',
      rowHoverBg: '#F1F3F5',
      borderColor: '#E9ECEF',
    },
    Menu: {
      itemBg: 'transparent',
      itemSelectedBg: 'rgba(37, 99, 235, 0.08)',
    },
    Modal: {
      contentBg: '#FFFFFF',
      headerBg: '#FFFFFF',
      titleColor: '#1A1D23',
    },
    Drawer: {
      colorBgElevated: '#FFFFFF',
    },
  },
}
