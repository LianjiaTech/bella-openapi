import { normalizeImageSource, parseSSEChunk, sanitizeBase64Payload, splitInlineImageText } from '../parseSSEChunk'

describe('parseSSEChunk', () => {
  it('parses custom image chunks that stream raw base64 in delta', () => {
    const parsed = parseSSEChunk('data: {"type":"image","delta":"iVBORw0KGgo="}')

    expect(parsed).toEqual({
      type: 'image',
      delta: 'iVBORw0KGgo=',
      url: undefined,
      image: {
        source: 'iVBORw0KGgo=',
        mimeType: undefined,
        append: true,
      },
    })
  })

  it('parses custom image chunks that return a complete base64 field', () => {
    const parsed = parseSSEChunk('data: {"type":"image","b64_json":"iVBORw0KGgo=","mime_type":"image/jpeg"}')

    expect(parsed).toEqual({
      type: 'image',
      url: undefined,
      image: {
        source: 'iVBORw0KGgo=',
        mimeType: 'image/jpeg',
        append: false,
      },
    })
  })

  it('parses image chunks without requiring delta', () => {
    const parsed = parseSSEChunk('data: {"type":"image","url":"https://example.com/image.png"}')

    expect(parsed).toEqual({
      type: 'image',
      url: 'https://example.com/image.png',
      image: {
        source: 'https://example.com/image.png',
        mimeType: undefined,
        append: false,
      },
    })
  })

  it('normalizes raw base64 into an image data URL', () => {
    expect(normalizeImageSource(' iVBORw0KGgo= ', 'image/png')).toBe(
      'data:image/png;base64,iVBORw0KGgo='
    )
  })

  it('keeps existing URL sources and normalizes image data URL payloads', () => {
    expect(normalizeImageSource('https://example.com/image.png')).toBe('https://example.com/image.png')
    expect(normalizeImageSource('data:image/png;base64,abc')).toBe('data:image/png;base64,abc=')
  })

  it('cleans whitespace from image data URLs before rendering', () => {
    expect(normalizeImageSource('data:image/png;base64,iVBO\nRw0K Ggo=')).toBe(
      'data:image/png;base64,iVBORw0KGgo='
    )
  })

  it('removes leaked inline tags from base64 payloads', () => {
    expect(normalizeImageSource('iVBORw0KGgo=</data></inline>')).toBe(
      'data:image/png;base64,iVBORw0KGgo='
    )
  })

  it('normalizes url-safe base64 and restores missing padding', () => {
    expect(sanitizeBase64Payload('abcd-_')).toBe('abcd+/==')
    expect(sanitizeBase64Payload('iVBORw0KGgo')).toBe('iVBORw0KGgo=')
  })

  it('drops invalid trailing characters before decoding base64', () => {
    expect(sanitizeBase64Payload('iVBORw0KGgo=###')).toBe('iVBORw0KGgo=')
  })

  it('removes repeated data-url prefixes from image payloads', () => {
    expect(sanitizeBase64Payload('data:image/png;base64,broken,data:image/png;base64,iVBORw0KGgo=')).toBe(
      'iVBORw0KGgo='
    )
  })

  it('trims impossible base64 tail lengths before decoding', () => {
    expect(sanitizeBase64Payload('abcde')).toBe('abcd')
  })

  it('produces payloads that browser atob can decode', () => {
    expect(() => window.atob(sanitizeBase64Payload('iVBORw0KGgo=。。。。'))).not.toThrow()
    expect(() => window.atob(sanitizeBase64Payload('abcd-_'))).not.toThrow()
    expect(() => window.atob(sanitizeBase64Payload('abcde'))).not.toThrow()
  })

  it('splits inline base64 image payloads out of text deltas', () => {
    expect(splitInlineImageText('已生成图片：<inline><data>iVBORw0KGgo=</data></inline>')).toEqual({
      insideImage: false,
      parts: [
        { type: 'text', content: '已生成图片：' },
        { type: 'image_start', mimeType: undefined },
        { type: 'image_data', content: 'iVBORw0KGgo=' },
        { type: 'image_end' },
      ],
    })
  })

  it('keeps inline image state across streamed text chunks', () => {
    const first = splitInlineImageText('小狗图片：<inline><data mime_type="image/png">iVBOR', false)
    expect(first).toEqual({
      insideImage: true,
      parts: [
        { type: 'text', content: '小狗图片：' },
        { type: 'image_start', mimeType: 'image/png' },
        { type: 'image_data', content: 'iVBOR' },
      ],
    })

    expect(splitInlineImageText('w0KGgo=</data></inline> 完成', first.insideImage)).toEqual({
      insideImage: false,
      parts: [
        { type: 'image_data', content: 'w0KGgo=' },
        { type: 'image_end' },
        { type: 'text', content: ' 完成' },
      ],
    })
  })
})
