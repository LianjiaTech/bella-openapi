import { convertPriceObj, convertSchemaLabels, formatPriceInfo } from '../price'
import { parsePriceRows } from '../image'

describe('image price display conversion', () => {
  it('converts image per-image prices between stored cents and displayed yuan', () => {
    const stored = {
      details: [
        {
          size: '1024x1024',
          ldPricePerImage: 3,
          mdPricePerImage: 6,
          hdPricePerImage: 10,
          textTokenPrice: 0.2,
          imageTokenPrice: 0.3,
          imageInputTokenPrice: 0.4,
          imageOutputTokenPrice: 0.5,
        },
      ],
      pricePerEdit: 5,
    }

    const display = convertPriceObj(stored, 'load')

    expect(display.details[0].ldPricePerImage).toBe(0.03)
    expect(display.details[0].mdPricePerImage).toBe(0.06)
    expect(display.details[0].hdPricePerImage).toBe(0.1)
    expect(display.details[0].textTokenPrice).toBe(2)
    expect(display.details[0].imageTokenPrice).toBe(3)
    expect(display.details[0].imageInputTokenPrice).toBe(4)
    expect(display.details[0].imageOutputTokenPrice).toBe(5)
    expect(display.pricePerEdit).toBe(0.05)
    expect(convertPriceObj(display, 'save')).toEqual(stored)
  })

  it('formats text-to-image model card prices as yuan per image', () => {
    const result = formatPriceInfo({
      unit: '分/张',
      priceInfo: {
        details: [
          {
            size: '1024x1024',
            ldPricePerImage: 3,
            mdPricePerImage: 6,
            hdPricePerImage: 10,
            textTokenPrice: 0.2,
            imageTokenPrice: 0.3,
            imageInputTokenPrice: 0.4,
            imageOutputTokenPrice: 0.5,
            unit: '分/张',
          },
        ],
      },
    }) as any

    expect(result.tag).toBe('textToImage')
    expect(result.unit).toBe('元/张')
    expect(result.data[0].unit).toBe('元/张')
    expect(result.data[0].ldPricePerImage).toBe(0.03)
    expect(result.data[0].mdPricePerImage).toBe(0.06)
    expect(result.data[0].hdPricePerImage).toBe(0.1)
    expect(result.data[0].textTokenPriceStr).toBe('2.00')
    expect(result.data[0].imageTokenPriceStr).toBe('3.00')
    expect(result.data[0].imageInputTokenPriceStr).toBe('4.00')
    expect(result.data[0].imageOutputTokenPriceStr).toBe('5.00')
  })

  it('formats image-edit display prices without converting token fields as per-image prices', () => {
    const result = formatPriceInfo({
      unit: '分/张',
      priceInfo: {
        pricePerEdit: 10,
        imageTokenPrice: 0.2,
      },
      displayPrice: {
        '单张图像价格（分/张）': '10',
        '图片token价格（/千token）': '0.2',
      },
    } as any) as any

    expect(result.tag).toBe('displayPrice')
    expect(result.unit).toBe('元/张')
    expect(result.data).toEqual([
      { label: '单张图像价格（元/张）', value: 0.1 },
      { label: '图片token价格（元/百万token）', value: 2 },
    ])
  })

  it('converts schema labels to frontend display units', () => {
    const result = convertSchemaLabels([
      { code: 'pricePerEdit', name: '单张图像价格（分/张）' },
      { code: 'imageTokenPrice', name: '图片token价格（/千token）' },
    ] as any)

    expect(result[0].name).toBe('单张图像价格（元/张）')
    expect(result[1].name).toBe('图片token价格（元/百万token）')
  })

  it('parses image playground price rows as yuan per image', () => {
    const result = parsePriceRows(
      {
        '价格详情': '尺寸：1024x1024\n低清：3\n中清：6\n高清：10',
      },
      '分/张'
    )

    expect(result).toEqual([
      {
        label: '价格详情（元/张）',
        lines: ['尺寸：1024x1024', '低清：0.03', '中清：0.06', '高清：0.1'],
      },
    ])
  })

  it('parses mixed image playground labels line by line', () => {
    const result = parsePriceRows(
      {
        '价格详情（按张价格单位：分/张，token价格单位：分/千token）':
          '尺寸：1024x1024\n低清：3\n中清：6\n高清：10\n文字token价格：0.2',
      },
      '分/张'
    )

    expect(result).toEqual([
      {
        label: '价格详情（按张价格单位：元/张，token价格单位：元/百万token）',
        lines: ['尺寸：1024x1024', '低清：0.03', '中清：0.06', '高清：0.1', '文字token价格：2.00'],
      },
    ])
  })

  it('parses image edit playground price and token rows separately', () => {
    const result = parsePriceRows(
      {
        '单张图像价格（分/张）': '10',
        '图片token价格（/千token）': '0.2',
      },
      '分/张'
    )

    expect(result).toEqual([
      { label: '单张图像价格（元/张）', lines: ['0.1'] },
      { label: '图片token价格（元/百万token）', lines: ['2.00'] },
    ])
  })

  it('keeps precision for sub-cent yuan image prices in playground', () => {
    const result = parsePriceRows(
      {
        '价格详情（按张价格单位：分/张，token价格单位：分/千token）':
          '低清：0.3\n中清：0.35\n高清：1',
      },
      '分/张'
    )

    expect(result[0].lines).toEqual(['低清：0.003', '中清：0.0035', '高清：0.01'])
  })
})
