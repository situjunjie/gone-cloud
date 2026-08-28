package cn.iocoder.yudao.module.devops.service.variablegroup;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VariableGroupParserTest {
    @Test
    void parsePreservesValuesAndEmptyValue() {
        var values = VariableGroupParser.parse("A=1\r\nB=a=b\nEMPTY=\n");
        assertEquals("1", values.get("A"));
        assertEquals("a=b", values.get("B"));
        assertEquals("", values.get("EMPTY"));
    }
    @Test
    void rejectsMalformedAndDuplicateKeys() {
        assertThrows(IllegalArgumentException.class, () -> VariableGroupParser.parse("bad"));
        assertThrows(IllegalStateException.class, () -> VariableGroupParser.parse("A=1\nA=2"));
    }
}
