import { convertPriceObj, convertSchemaLabels, convertSpeakerHourlyPriceObj, convertTtsPriceObj, convertVideoDurationPriceObj, formatPriceInfo } from '../price'
import { parseImageEditPriceRows, parsePriceRows } from '../image'

describe('image price display conversion', () => {
  it('keeps TTS character prices unchanged when loading and saving', () => {
    const stored = {
      input: 100,
      unit: '分/万字',
      batchDiscount: 0.8,
      supplierDiscount: 0.9,
    }

    const display = convertTtsPriceObj(stored, 'load')

    expect(display).toEqual(stored)
    expect(convertTtsPriceObj(display, 'save')).toEqual(stored)
  })

  it('converts video token prices between stored cents per thousand tokens and displayed yuan per million tokens', () => {
    const stored = {
      billingMode: 'token',
      input: 1.5,
      output: 2.75,
      textOutput: 9,
      videoOutput: 17.5,
    }

    const display = convertVideoDurationPriceObj(stored, 'load')

    expect(display.input).toBe(15)
    expect(display.output).toBe(27.5)
    expect(display.textOutput).toBe(90)
    expect(display.videoOutput).toBe(175)
    expect(convertVideoDurationPriceObj(display, 'save')).toEqual(stored)
  })

  it.each([
    '/v1/audio/speaker/embedding',
    '/v1/audio/speaker/diarization',
  ])('converts %s hourly prices between stored cents and displayed yuan', (endpoint) => {
    const stored = {
      price: 123.45,
      batchDiscount: 0.8,
      supplierDiscount: 0.9,
    }

    const display = convertSpeakerHourlyPriceObj(stored, 'load', endpoint)

    expect(display).toEqual({
      price: 1.2345,
      batchDiscount: 0.8,
      supplierDiscount: 0.9,
    })
    expect(convertSpeakerHourlyPriceObj(display, 'save', endpoint)).toEqual(stored)
  })

  it('leaves non-speaker hourly-looking price fields unchanged', () => {
    const stored = { price: 123.45 }

    expect(convertSpeakerHourlyPriceObj(stored, 'load', '/v1/audio/transcriptions')).toEqual(stored)
  })

  it('converts video duration prices between stored cents per second and displayed yuan per second', () => {
    const stored = {
      billingMode: 'duration',
      details: [
        { resolution: '720', audio: false, pricePerSecond: 60 },
        { resolution: '1080', audio: true, pricePerSecond: 120 },
      ],
      inputAudioPricePerSecond: 0,
    }

    const display = convertVideoDurationPriceObj(stored, 'load')

    expect(display.details[0].pricePerSecond).toBe(0.6)
    expect(display.details[1].pricePerSecond).toBe(1.2)
    expect(display.inputAudioPricePerSecond).toBe(0)
    expect(convertVideoDurationPriceObj(display, 'save')).toEqual(stored)
  })

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
      textTokenPrice: 0.6,
      imageTokenPrice: 0.7,
      imageInputTokenPrice: 0.8,
      imageOutputTokenPrice: 0.9,
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
    expect(display.textTokenPrice).toBe(6)
    expect(display.imageTokenPrice).toBe(7)
    expect(display.imageInputTokenPrice).toBe(8)
    expect(display.imageOutputTokenPrice).toBe(9)
    expect(convertPriceObj(display, 'save')).toEqual(stored)
  })

  it('formats speaker model card prices as yuan per hour', () => {
    expect(formatPriceInfo({
      unit: '分/小时',
      priceInfo: { price: 250 },
    })).toEqual({
      tag: 'price',
      unit: '元/小时',
      price: '2.50',
      batchDiscount: undefined,
      supplierDiscount: undefined,
    })
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

  it('formats video duration model card prices as yuan per second', () => {
    const result = formatPriceInfo({
      unit: '分/秒',
      priceInfo: {
        billingMode: 'duration',
        details: [
          { resolution: '720', audio: false, pricePerSecond: 60 },
          { resolution: '1080', audio: true, pricePerSecond: 120 },
        ],
      },
    }) as any

    expect(result.tag).toBe('displayPrice')
    expect(result.unit).toBe('元/秒')
    expect(result.data).toEqual([
      { label: '720P / 无声', value: 0.6 },
      { label: '1080P / 有声', value: 1.2 },
    ])
  })

  it('formats minimax video prices with positive input material prices', () => {
    const result = formatPriceInfo({
      unit: '分/秒',
      priceInfo: {
        billingMode: 'minimax',
        details: [
          { resolution: '2K', pricePerSecond: 80 },
          { resolution: '768', pricePerSecond: 50 },
        ],
        inputImagePricePerImage: 20,
        inputAudioPricePerSecond: 10,
        freeInputImageCount: 5,
      },
    }) as any

    expect(result.tag).toBe('displayPrice')
    expect(result.unit).toBe('按视频秒数和输入素材分别计费')
    expect(result.data).toEqual([
      { label: '视频 / 2K / 元/秒', value: 0.8 },
      { label: '视频 / 768P / 元/秒', value: 0.5 },
      { label: '输入图片（超过 5 张）/ 元/张', value: 0.2 },
      { label: '输入音频 / 元/秒', value: 0.1 },
    ])
  })

  it('hides zero minimax input material prices', () => {
    const result = formatPriceInfo({
      unit: '分/秒',
      priceInfo: {
        billingMode: 'minimax',
        details: [
          { resolution: '480P', pricePerSecond: 33 },
          { resolution: '768P', pricePerSecond: 50 },
        ],
        inputImagePricePerImage: 0,
        inputAudioPricePerSecond: 0,
        freeInputImageCount: 0,
      },
    }) as any

    expect(result.tag).toBe('displayPrice')
    expect(result.data).toEqual([
      { label: '视频 / 480P / 元/秒', value: 0.33 },
      { label: '视频 / 768P / 元/秒', value: 0.5 },
    ])
  })

  it('formats video token model card prices as yuan per million tokens', () => {
    const result = formatPriceInfo({
      unit: '分/千token 或 分/秒',
      priceInfo: {
        input: 1.5,
        output: 1.5,
        details: [],
      },
    }) as any

    expect(result.tag).toBe('displayPrice')
    expect(result.unit).toBe('元/百万token')
    expect(result.data).toEqual([
      { label: '输入token单价（元/百万token）', value: '15.00' },
      { label: '输出token单价（元/百万token）', value: '15.00' },
    ])
  })

  it('formats gemini video prices with separate text and video output rates', () => {
    const result = formatPriceInfo({
      unit: '分/千token 或 分/秒',
      priceInfo: {
        billingMode: 'gemini',
        input: 1.5,
        textOutput: 9,
        videoOutput: 17.5,
      },
    }) as any

    expect(result.tag).toBe('displayPrice')
    expect(result.unit).toBe('元/百万token')
    expect(result.data).toEqual([
      { label: '输入token单价（元/百万token）', value: '15.00' },
      { label: '文本输出token单价（元/百万token）', value: '90.00' },
      { label: '视频输出token单价（元/百万token）', value: '175.00' },
    ])
  })

  it('only exposes the per-image price when image-edit pricing contains internal token fields', () => {
    const result = formatPriceInfo({
      unit: '分/张',
      priceInfo: {
        billingMode: 'mixed',
        pricePerEdit: 10,
        textTokenPrice: 0.1,
        imageTokenPrice: 0.2,
        imageInputTokenPrice: 0.3,
        imageOutputTokenPrice: 0.4,
      },
      displayPrice: {
        '计费模式：per_image（仅按张）、token（仅token）、mixed（按张+token）；为空保持历史逻辑': 'mixed',
        '单张图像价格（分/张）': '10',
        '文字token价格（分/千token）': '0.1',
        '输入图片token价格（分/千token）': '0.3',
        '输出图片token价格（分/千token）': '0.4',
        '图片token兜底价格（分/千token），兼容旧字段': '0.2',
      },
    } as any) as any

    expect(result.tag).toBe('displayPrice')
    expect(result.unit).toBe('元/张')
    expect(result.data).toEqual([
      { label: '单张图像价格（元/张）', value: 0.1 },
    ])
  })

  it('exposes a single user-facing image token price when no per-image price exists', () => {
    const result = formatPriceInfo({
      unit: '分/张',
      priceInfo: {
        billingMode: 'token',
        imageTokenPrice: 99,
        imageOutputTokenPrice: 0.2,
      },
      displayPrice: {
        '计费模式：per_image（仅按张）、token（仅token）、mixed（按张+token）；为空保持历史逻辑': 'token',
        '输出图片token价格（分/千token）': '0.2',
        '图片token兜底价格（分/千token），兼容旧字段': '99',
      },
    } as any) as any

    expect(result.unit).toBe('元/百万token')
    expect(result.data).toEqual([
      { label: '图片token（元/百万token）', value: '2.00' },
    ])
  })

  it('uses the legacy image token field only when it is the available public price', () => {
    const result = formatPriceInfo({
      unit: '分/张',
      priceInfo: {
        billingMode: 'token',
        imageTokenPrice: 0.2,
      },
      displayPrice: {
        '图片token兜底价格（分/千token），兼容旧字段': '0.2',
      },
    } as any) as any

    expect(result.data).toEqual([
      { label: '图片token（元/百万token）', value: '2.00' },
    ])
  })

  it('shows only the per-image price in image-edit playground model info', () => {
    const result = parseImageEditPriceRows({
      unit: '分/张',
      priceInfo: {
        pricePerEdit: 20,
        imageTokenPrice: 0.2,
      },
      displayPrice: {
        '单张图像价格（分/张）': '20',
        '图片token兜底价格（分/千token），兼容旧字段': '0.2',
      },
    })

    expect(result).toEqual([
      { label: '单张图像价格（元/张）', lines: ['0.2'] },
    ])
  })

  it('shows one public image token price in image-edit playground model info', () => {
    const result = parseImageEditPriceRows({
      unit: '分/张',
      priceInfo: {
        imageTokenPrice: 0.2,
      },
      displayPrice: {
        '文字token价格（分/千token）': '0.1',
        '图片token兜底价格（分/千token），兼容旧字段': '0.2',
      },
    })

    expect(result).toEqual([
      { label: '图片token（元/百万token）', lines: ['2.00'] },
    ])
  })

  it('omits an empty legacy image-edit billing mode from display prices', () => {
    const result = formatPriceInfo({
      unit: '分/张',
      priceInfo: {
        billingMode: '',
        pricePerEdit: 7,
      },
      displayPrice: {
        '计费模式：per_image（仅按张）、token（仅token）、mixed（按张+token）；为空保持历史逻辑': '',
        '单张图像价格（分/张）': '7',
      },
    } as any) as any

    expect(result.data).toEqual([
      { label: '单张图像价格（元/张）', value: 0.07 },
    ])
  })

  it('converts schema labels to frontend display units', () => {
    const result = convertSchemaLabels([
      { code: 'pricePerEdit', name: '单张图像价格（分/张）' },
      { code: 'imageTokenPrice', name: '图片token价格（/千token）' },
      { code: 'textTokenPrice', name: '文字token价格（分/千token）' },
      { code: 'imageInputTokenPrice', name: '输入图片token价格（分/千token）' },
      { code: 'imageOutputTokenPrice', name: '输出图片token价格（分/千token）' },
      { code: 'price', name: '每小时价格（分/小时）' },
    ] as any)

    expect(result[0].name).toBe('单张图像价格（元/张）')
    expect(result[1].name).toBe('图片token价格（元/百万token）')
    expect(result[2].name).toBe('文字token价格（元/百万token）')
    expect(result[3].name).toBe('输入图片token价格（元/百万token）')
    expect(result[4].name).toBe('输出图片token价格（元/百万token）')
    expect(result[5].name).toBe('每小时价格（元/小时）')
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
