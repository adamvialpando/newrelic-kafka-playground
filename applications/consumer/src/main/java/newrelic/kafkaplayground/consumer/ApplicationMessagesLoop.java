package newrelic.kafkaplayground.consumer;

import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.Trace;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Properties;

public class ApplicationMessagesLoop implements Runnable {

    final static Logger logger = LoggerFactory.getLogger(ApplicationMessagesLoop.class);

    private final KafkaConsumer<String, String> consumer;
    private final List<String> topics;
    
    private void processMessage(ConsumerRecord<String, String> record) {
        Iterable<Header> headers = record.headers().headers("newrelic");
        for (Header header : headers) {
            String nrpayload = new String(header.value(), StandardCharsets.UTF_8);
            NewRelic.getAgent().getTransaction().acceptDistributedTracePayload(nrpayload);
        }

        // annotate this span with metadata from the record
        NewRelic.getAgent().getTracedMethod().addCustomAttribute("kafka.consumer.record.topic", record.topic());
        NewRelic.getAgent().getTracedMethod().addCustomAttribute("kafka.consumer.record.partition", record.partition());
        NewRelic.getAgent().getTracedMethod().addCustomAttribute("kafka.consumer.record.offset", record.offset());
        NewRelic.getAgent().getTracedMethod().addCustomAttribute("kafka.consumer.record.timestamp", record.timestamp());

        // only log if the trace is sampled to demonstrate logs-in-context
        if (NewRelic.getAgent().getTraceMetadata().isSampled()) {
            logger.info("[Consumer memberId={}, groupId={}] consumed message: {}", this.consumer.groupMetadata().memberId(), this.consumer.groupMetadata().groupId(), record.value());
        }

    }

    public ApplicationMessagesLoop(List<String> topics, Properties consumerProperites) {
        this.topics = topics;
        this.consumer = new KafkaConsumer<>(consumerProperites);
    }
    @Trace(dispatcher = true)
    @Override
    public void run() {
            // randomly sleep for a long time to simulate extreme lag
            if (Math.random() < 0.1) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    NewRelic.noticeError(e);
                    throw new RuntimeException(e);
                    
                }
            }
    
        try {
            NotifyOnRebalance nor = new NotifyOnRebalance();
            consumer.subscribe(topics, nor);

            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(Long.MAX_VALUE));
                for (ConsumerRecord<String, String> record : records) {
                    processMessage(record);
                    // do somthing to increase cpu load
                    // try {
                    //     Thread.sleep(Math.round(Math.random()));
                    // } catch (InterruptedException e) {
                    //     logger.error("Interrupted", e);
                    //     NewRelic.noticeError(e);
                    // }
                    if (Math.random() < 0.1) {
                        //new relic custom parameter indicating consumer rebalance
                        NewRelic.getAgent().getTracedMethod().addCustomAttribute("kafka.consumer.rebalance", true);
                    }
                }
            }
        } catch (WakeupException e) {
            NewRelic.noticeError(e);
        } finally {
            consumer.close();
        }
    }

    public void shutdown() {
        consumer.wakeup();
    }
}