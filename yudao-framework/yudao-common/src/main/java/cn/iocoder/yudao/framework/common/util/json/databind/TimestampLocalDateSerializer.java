package cn.iocoder.yudao.framework.common.util.json.databind;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 基于时间戳的 LocalDate 序列化器
 *
 * @author 老五
 */
public class TimestampLocalDateSerializer extends JsonSerializer<LocalDate> {

    public static final TimestampLocalDateSerializer INSTANCE = new TimestampLocalDateSerializer();

    @Override
    public void serialize(LocalDate value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeNumber(value.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

}
