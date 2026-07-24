/**
 * TemperatureSlider 组件属性类型
 */
export interface TemperatureSliderProps {
  /** 当前温度值 (0-2) */
  value: number;
  /** 温度值变化回调 */
  onChange: (value: number) => void;
}
