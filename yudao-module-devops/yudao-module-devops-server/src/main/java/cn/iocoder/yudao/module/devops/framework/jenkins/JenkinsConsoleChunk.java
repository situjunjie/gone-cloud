package cn.iocoder.yudao.module.devops.framework.jenkins;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Jenkins progressive console text chunk.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JenkinsConsoleChunk {

    /**
     * Console text returned for the current offset.
     */
    private String text;
    /**
     * Offset for the next progressiveText request.
     */
    private Long nextStart;
    /**
     * Whether Jenkins still has more data for this build.
     */
    private Boolean moreData;

}
