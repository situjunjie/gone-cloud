package cn.iocoder.yudao.framework.jackson.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class YudaoJacksonAutoConfigurationTest {

    private final ObjectMapper objectMapper = createObjectMapper();

    @Test
    void testLocalDateSerializeAsStartOfDayEpochMillis() throws Exception {
        LocalDate date = LocalDate.of(2026, 7, 29);
        long expected = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();

        assertEquals("{\"date\":" + expected + "}", objectMapper.writeValueAsString(new LocalDateHolder(date)));
    }

    @Test
    void testLocalDateDeserializeAsIsoDate() throws Exception {
        assertEquals(new LocalDateHolder(LocalDate.of(2026, 7, 29)),
                objectMapper.readValue("{\"date\":\"2026-07-29\"}", LocalDateHolder.class));
    }

    @Test
    void testLocalDateTimeSerializeAsEpochMillis() throws Exception {
        LocalDateTime dateTime = LocalDateTime.of(2026, 7, 29, 12, 30);
        long expected = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        assertEquals("{\"dateTime\":" + expected + "}", objectMapper.writeValueAsString(new LocalDateTimeHolder(dateTime)));
    }

    private static ObjectMapper createObjectMapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new YudaoJacksonAutoConfiguration().ldtEpochMillisCustomizer().customize(builder);
        return builder.build();
    }

    private record LocalDateHolder(LocalDate date) {
    }

    private record LocalDateTimeHolder(LocalDateTime dateTime) {
    }

}
