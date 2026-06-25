package cn.iocoder.yudao.module.devops.service.pipeline.execution;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.PipelineRunLogLineRespVO;
import lombok.Getter;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 流水线日志 SSE 广播器。
 */
@Component
public class PipelineLogBroadcaster {

    private final ConcurrentHashMap<Long, Set<Subscription>> subscriptions = new ConcurrentHashMap<>();

    public Subscription subscribe(Long pipelineRunId, String stepId, Long afterId, SseEmitter emitter) {
        Subscription subscription = new Subscription(pipelineRunId, blankToNull(stepId),
                afterId == null ? 0L : afterId, emitter);
        subscriptions.computeIfAbsent(pipelineRunId, key -> ConcurrentHashMap.newKeySet()).add(subscription);
        Runnable close = () -> unsubscribe(subscription);
        emitter.onCompletion(close);
        emitter.onTimeout(close);
        emitter.onError(ex -> close.run());
        return subscription;
    }

    public void unsubscribe(Subscription subscription) {
        if (subscription == null || !subscription.close()) {
            return;
        }
        Set<Subscription> runSubscriptions = subscriptions.get(subscription.getPipelineRunId());
        if (runSubscriptions == null) {
            return;
        }
        runSubscriptions.remove(subscription);
        if (runSubscriptions.isEmpty()) {
            subscriptions.remove(subscription.getPipelineRunId(), runSubscriptions);
        }
    }

    public void publish(PipelineRunLogLineRespVO line) {
        if (line == null || line.getPipelineRunId() == null) {
            return;
        }
        Set<Subscription> runSubscriptions = subscriptions.get(line.getPipelineRunId());
        if (runSubscriptions == null || runSubscriptions.isEmpty()) {
            return;
        }
        for (Subscription subscription : runSubscriptions) {
            if (!subscription.send(line)) {
                unsubscribe(subscription);
            }
        }
    }

    private String blankToNull(String value) {
        return StrUtil.isBlank(value) ? null : value;
    }

    public static class Subscription {

        @Getter
        private final Long pipelineRunId;
        private final String stepId;
        private final AtomicLong cursor;
        private final SseEmitter emitter;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private Subscription(Long pipelineRunId, String stepId, Long cursor, SseEmitter emitter) {
            this.pipelineRunId = pipelineRunId;
            this.stepId = stepId;
            this.cursor = new AtomicLong(cursor);
            this.emitter = emitter;
        }

        public boolean send(PipelineRunLogLineRespVO line) {
            if (closed.get() || line == null || line.getId() == null) {
                return false;
            }
            if (stepId != null && !stepId.equals(line.getStepId())) {
                return true;
            }
            long currentCursor = cursor.get();
            if (line.getId() <= currentCursor) {
                return true;
            }
            synchronized (emitter) {
                if (closed.get() || line.getId() <= cursor.get()) {
                    return !closed.get();
                }
                try {
                    emitter.send(SseEmitter.event().name("log-line").id(String.valueOf(line.getId())).data(line));
                    cursor.set(line.getId());
                    return true;
                } catch (IOException | IllegalStateException ex) {
                    closed.set(true);
                    return false;
                }
            }
        }

        public boolean close() {
            return closed.compareAndSet(false, true);
        }

        public boolean isClosed() {
            return closed.get();
        }

    }

}
