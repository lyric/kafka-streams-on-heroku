package io.jeffchao.streams.aggregator;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/*
TODO(Jeff): sinking to postgres + make dataclip
TODO(Jeff): Threshold trigger topology
TODO(Jeff): tests
*/
public class WordCount {

  private static final Logger log = LoggerFactory.getLogger(WordCount.class);

  private static final String TOPIC_PREFIX =
      Optional.ofNullable(System.getenv("TOPIC_PREFIX")).orElse("");

  public static void main(String[] args) throws CertificateException, NoSuchAlgorithmException,
      KeyStoreException, IOException, URISyntaxException {
    Properties streamsConfig = new AggregatorConfig().getProperties();

    final Serde<String> stringSerde = Serdes.String();
    final Serde<Long> longSerde = Serdes.Long();

    final StreamsBuilder builder = new StreamsBuilder();

    final KStream<String, String> textLines =
        builder.stream(String.format("%stextlines", TOPIC_PREFIX));

    final Pattern pattern = Pattern.compile("\\W+", Pattern.UNICODE_CHARACTER_CLASS);

    final KTable<String, Long> wordCounts = textLines
        .flatMapValues(value -> Arrays.asList(pattern.split(value.toLowerCase())))
        .groupBy((key, word) -> word)
        .count(Materialized.as("counts"));

    wordCounts.toStream()
        .to(String.format("%swordswithcounts", TOPIC_PREFIX),
            Produced.with(stringSerde, longSerde));

    final KafkaStreams streams = new KafkaStreams(builder.build(), streamsConfig);

    streams.cleanUp();
    streams.start();

    Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
  }

}