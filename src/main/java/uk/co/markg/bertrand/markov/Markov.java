package uk.co.markg.bertrand.markov;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.co.markg.bertrand.database.MessageRepository;

public class Markov {

  private static final Logger logger = LogManager.getLogger(Markov.class);
  private static final WeightedCollection SENTENCE_ENDS = getSentenceEnds();
  private static final String END_WORD = "END_WORD";
  private static final List<String> VALID_END_WORD_STOPS = List.of("?", "!", ".");
  private Map<String, WeightedCollection> wordMap;
  private Set<String> startWords;
  private Set<String> endWords;

  private Markov(List<String> inputs) {
    wordMap = new HashMap<String, WeightedCollection>();
    startWords = new HashSet<>();
    endWords = new HashSet<>();
    parseInput(inputs);
  }

  /**
   * Creates a collection of sentence ends with probabilities taken from a subset of user messages.
   * 
   * @return the collection of sentence ends
   */
  private static WeightedCollection getSentenceEnds() {
    var collection = new WeightedCollection();
    collection.add(new WeightedElement(".", 0.4369));
    collection.add(new WeightedElement("!", 0.1660));
    collection.add(new WeightedElement("?", 0.2733));
    collection.add(new WeightedElement("!!", 0.0132));
    collection.add(new WeightedElement("??", 0.0114));
    collection.add(new WeightedElement("!?", 0.0027));
    collection.add(new WeightedElement("...", 0.0965));
    return collection;
  }

  public static Markov load(long userid) {
    return load(List.of(userid));
  }

  public static Markov load(List<Long> userids) {
    logger.info("Loaded chain for {}", userids);
    var inputs = MessageRepository.getRepository().getByUsers(userids);
    return new Markov(inputs);
  }

  /**
   * Convenience method to generate multiple sentences
   * 
   * @return the sentences joined together by a space character
   */
  public String generateRandom() {
    int sentences = ThreadLocalRandom.current().nextInt(5) + 1;
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < sentences; i++) {
      sb.append(generate()).append(" ");
    }
    return sb.toString();
  }

  private String getStartWord(int startNo) {
    Iterator<String> itr = startWords.iterator();
    for (int i = 0; i < startNo; i++) {
      itr.next();
    }
    return itr.next();
  }

  /**
   * Generates a sentence from the markov chain
   * 
   * @return a complete sentence
   */
  public String generate() {
    int startNo = ThreadLocalRandom.current().nextInt(startWords.size());
    String word = getStartWord(startNo);
    List<String> sentence = new ArrayList<>();
    sentence.add(word);
    boolean endWordHit = false;
    while (!endWordHit) {
      var nextEntry = wordMap.get(word);
      word = nextEntry.getRandom().map(WeightedElement::getElement).orElse("");
      if (endWords.contains(word)) {
        endWordHit = true;
      }
      if (word.equals(END_WORD)) {
        break;
      }
      sentence.add(word);
    }
    String s = String.join(" ", sentence);
    logger.debug("Generated: {}", s);
    if (s.matches("(.*[^.!?`+>\\-=_+:@~;'#\\[\\]{}\\(\\)\\/\\|\\\\]$)")) {
      s = s + SENTENCE_ENDS.getRandom().map(WeightedElement::getElement).orElse("@@@@@@@");
    }
    return s;
  }

  /**
   * Convenience method to parse multiple sentences
   * 
   * @param inputs the list of sentences
   */
  private void parseInput(List<String> inputs) {
    for (String input : inputs) {
      parseInput(input);
    }
  }

  /**
   * Parses a sentence into the word frequency map
   * 
   * @param input the sentence to parse
   */
  private void parseInput(String input) {
    String[] tokens = input.split("\\s+\\v?");
    if (tokens.length < 3) {
      throw new IllegalArgumentException(
          "Input '" + input + "'is too short. Must be greater than 3 tokens.");
    }
    for (int i = 0; i < tokens.length; i++) {
      String word = tokens[i];
      if (word.isEmpty()) {
        continue;
      }
      if (i == 0) {
        startWords.add(word);
      } else if (isEndWord(word)) {
        endWords.add(word);
        insertWordFrequency(word, END_WORD);
        continue;
      }
      if (i == tokens.length - 1) {
        insertWordFrequency(word, END_WORD);
        break;
      }
      String nextWord = tokens[i + 1];
      if (nextWord.isEmpty()) {
        continue;
      }
      if (wordMap.containsKey(word)) {
        updateWordFrequency(word, nextWord);
      } else {
        insertWordFrequency(word, nextWord);
      }
    }
  }

  /**
   * Checks whether a word can be matched as an end word. i.e. the word ends a sentence.
   * 
   * @param word the word to check
   * @return true if the word can be matched as an end word
   */
  private boolean isEndWord(String word) {
    for (String stop : VALID_END_WORD_STOPS) {
      if (word.endsWith(stop)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Inserts a new word and follow word into the wordFrequencyMap
   * 
   * @param word       the main word
   * @param followWord the follow word
   */
  private void insertWordFrequency(String word, String followWord) {
    var wc = new WeightedCollection();
    wc.add(new WeightedElement(followWord, 1));
    wordMap.put(word, wc);
  }

  /**
   * Updates the follow word frequency of a word in the wordFrequencyMap
   * 
   * @param key        the main word
   * @param followWord the follow word
   */
  private void updateWordFrequency(String key, String followWord) {
    var followFrequency = wordMap.get(key);
    followFrequency.get(followWord).ifPresentOrElse(
        fw -> followFrequency.update(fw, fw.getWeight() + 1),
        () -> followFrequency.add(new WeightedElement(followWord, 1)));
  }
}
