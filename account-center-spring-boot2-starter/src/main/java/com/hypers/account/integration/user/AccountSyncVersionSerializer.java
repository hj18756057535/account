package com.hypers.account.integration.user;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;

/** 同步版本按协议写为 JSON 整数，用户 ID 仍为字符串。 */
public class AccountSyncVersionSerializer extends JsonSerializer<Long> {
    @Override
    public void serialize(Long value, JsonGenerator output, SerializerProvider provider) throws IOException {
        output.writeNumber(value);
    }
}

