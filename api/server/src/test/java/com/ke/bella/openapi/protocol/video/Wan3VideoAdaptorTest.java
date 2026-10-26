package com.ke.bella.openapi.protocol.video;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class Wan3VideoAdaptorTest {
    @Test
    public void exposesWanProtocolMetadata() {
        Wan3VideoAdaptor adaptor = new Wan3VideoAdaptor();
        assertEquals("阿里云万相3.0视频生成", adaptor.getDescription());
        assertEquals(Wan3VideoProperty.class, adaptor.getPropertyClass());
    }
}
