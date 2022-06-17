package newrelic.kafkaplayground.producer;

import com.newrelic.api.agent.DistributedTracePayload;
import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.Token;
import com.newrelic.api.agent.Trace;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.KafkaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.UUID;

@WebServlet(name = "MessageServlet", urlPatterns = {"/error"}, loadOnStartup = 1, initParams = {
        @WebInitParam(name = "applicationTopicName", value = "application-messages")})
public class ErrorServlet extends HttpServlet {

    final Logger logger = LoggerFactory.getLogger(ErrorServlet.class);

    private String applicationTopicName;

    @Override
    public void init(ServletConfig ctx) throws ServletException {

        String applicationTopicNameEnv = System.getenv("APPLICATION_MESSAGES_TOPIC_NAME");
        if (applicationTopicNameEnv != null) {
            this.applicationTopicName = applicationTopicNameEnv;
        } else {
            this.applicationTopicName = ctx.getInitParameter(applicationTopicName);
        
        }

    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        Properties producerProps = (Properties) request.getServletContext().getAttribute("producerProps");
        @SuppressWarnings("unchecked")
        KafkaProducer<String, String> producer = (KafkaProducer) request.getServletContext().getAttribute("kafkaProducer");

        Token transactionToken = NewRelic.getAgent().getTransaction().getToken();

        String userId = (String) request.getAttribute("userId");
        NewRelic.addCustomParameter("user.id", userId);

        String messageId = UUID.randomUUID().toString();
        NewRelic.addCustomParameter("message.id", messageId);

        String payload = String.format("{ \"userId\": %s, \"messageId\": %s}", userId, messageId);
        ProducerRecord<String, String> record = new ProducerRecord<String, String>(this.applicationTopicName, userId, payload);


        final DistributedTracePayload dtPayload = NewRelic.getAgent().getTransaction().createDistributedTracePayload();
        record.headers().add("newrelic", dtPayload.text().getBytes(StandardCharsets.UTF_8));

        // only log if the trace is sampled to demonstrate logs-in-context
        if (NewRelic.getAgent().getTraceMetadata().isSampled()) {
            logger.info("[Producer clientId={}] Sending message {}", producerProps.getProperty("client.id"), payload);
        }

        producer.send(record,
                new Callback() {
                    @Trace(async = true)
                    //start new relic transaction
                    public void onCompletion(RecordMetadata metadata, Exception e) {
                        transactionToken.linkAndExpire();
                        NewRelic.getAgent().getTracedMethod().addCustomAttribute("kafka.producer.config.client.id", producerProps.getProperty("client.id"));
                        //randomly throw a Kafka exception to demonstrate error handling
                        if (Math.random() < 0.5) {
                            throw new KafkaException("Kafka exception");
                            
                        }
                        // randomly throw UnsupportedOperationException("Unsupported operation exception")
                        if (Math.random() < 0.5) {
                            throw new UnsupportedOperationException("Unsupported operation exception");
                        }
                        //randomly throw IllegalArgumentException("Illegal argument exception"),
                        if (Math.random() < 0.5) {
                            throw new IllegalArgumentException("Illegal argument exception");
                        }

                        if (e != null) {
                            logger.error("Got an error (asynchronously) when sending message {}", messageId, e);
                        } else {
                            // annotate the span with the metadta
                            if (metadata.hasOffset()) {
                                NewRelic.getAgent().getTracedMethod().addCustomAttribute("kafka.producer.record.offset", metadata.offset());
                            }
                            if (metadata.hasTimestamp()) {
                                NewRelic.getAgent().getTracedMethod().addCustomAttribute("kafka.producer.record.timestamp", metadata.timestamp());
                            }
                            NewRelic.getAgent().getTracedMethod().addCustomAttribute("kafka.producer.record.partition", metadata.partition());
                            NewRelic.getAgent().getTracedMethod().addCustomAttribute("kafka.producer.record.topic", metadata.topic());

                            // only log if the trace is sampled to demonstrate logs-in-context
                            if (NewRelic.getAgent().getTraceMetadata().isSampled()) {
                                logger.info("[Producer clientId={}] Message {} send complete", producerProps.getProperty("client.id"), messageId);
                            }
                        }
                    }
                });


        PrintWriter out = response.getWriter();
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        out.print(payload);
        out.flush();
    }
}