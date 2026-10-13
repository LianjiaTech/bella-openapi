import {
  initializeValuesFromSchema,
  serializeToJson,
} from '../formHelpers'
import type { JsonSchema } from '@/lib/types/metadata'

const schema: JsonSchema = {
  params: [
    {
      code: 'safetyCheckMode',
      name: '安全检测模式',
      valueType: 'enum',
      selections: ['skip', 'async', 'sync'],
    },
    { code: 'deployName', name: '部署名称', valueType: 'string' },
  ],
}

describe('initializeValuesFromSchema', () => {
  it('initializes an LLM safety check mode to sync', () => {
    const values = initializeValuesFromSchema(schema)

    expect(values.safetyCheckMode).toBe('sync')
    expect(JSON.parse(serializeToJson(values))).toMatchObject({
      safetyCheckMode: 'sync',
    })
  })

  it('preserves an explicitly configured safety check mode', () => {
    const values = initializeValuesFromSchema(
      schema,
      '{"safetyCheckMode":"async"}'
    )

    expect(values.safetyCheckMode).toBe('async')
  })
})
