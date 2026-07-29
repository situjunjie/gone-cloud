package cn.iocoder.yudao.framework.common.util.json.databind;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NumberSerializerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new SimpleModule()
            .addSerializer(Long.class, NumberSerializer.INSTANCE)
            .addSerializer(Long.TYPE, NumberSerializer.INSTANCE));

    @Test
    void testSerializeLongWithoutJavaScriptPrecisionLoss() throws Exception {
        assertEquals("{\"id\":9007199254740990}", objectMapper.writeValueAsString(new IdHolder(9007199254740990L)));
        assertEquals("{\"id\":\"9007199254740991\"}", objectMapper.writeValueAsString(new IdHolder(9007199254740991L)));
        assertEquals("{\"id\":\"9223372036854775807\"}", objectMapper.writeValueAsString(new IdHolder(Long.MAX_VALUE)));
    }

    private record IdHolder(Long id) {
    }

}
