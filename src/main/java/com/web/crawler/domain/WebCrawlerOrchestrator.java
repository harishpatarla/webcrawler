package com.web.crawler.domain;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.helper.StringUtil;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
public class WebCrawlerOrchestrator {
   private static final Pattern DOMAIN_NAME_PATTERN
      = Pattern.compile("([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,6}");
   private Matcher matcher;
   List<String> downloadedPageContent = new ArrayList<>();
   /**
    * Below executor can be used leaving one of the core for tasks other than web crawling
    */
   //private ExecutorService executor = Executors.newFixedThreadPool(threadsForComputation());

   private final ExecutorService executor = Executors.newCachedThreadPool();


   /**
    * @param searchTerm This method is driving the steps 1-5 given in the problem
    *                   I am using the executor to have it run async for time consuming tasks
    */
   public void webCrawler(String searchTerm) {
      CompletableFuture.completedFuture(searchTerm)
         .thenComposeAsync(this::getDataFromGoogle, executor)
         //.exceptionally(ex -> new Document(""))
         //.completeOnTimeout(new Document(""), 60, TimeUnit.SECONDS)
         .thenApply(this::extractLinks)
         .thenCompose(this::downloadPages)
         //.exceptionally(ex -> Collections.emptyList())
         .thenAccept(this::getLibraries);
   }

   /**
    * @param searchTerm This method takes the searchTerm as string and finds asynchronously the results.
    *                   There is a timeout set to ensure this does not execute forever
    * @return
    */
   private CompletableFuture<Document> getDataFromGoogle(String searchTerm) {
      return CompletableFuture.supplyAsync(() -> {
         String request = "https://www.google.com/search?q=" + searchTerm + "&num=50000";
         log.info("Sending request={}", request);
         Document document = null;
         try {
            document = Jsoup
               .connect(request)
               .userAgent(
                  "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)")
               .timeout(100000).get();
         } catch (IOException e) {
            e.printStackTrace();
         }
         return document;
      }, executor);
   }

   /**
    * @param document Takes document and finds all the links. Stream through those links and construct a URL
    *                 and add it to the searchResults. A hashset is used to ensure duplicates values are not allowed.
    * @return
    */
   private Set<URL> extractLinks(Document document) {
      Set<URL> searchResults = new HashSet<>();
      Elements links = document.select("a[href]");
      links.stream()
         .map(link -> link.attr("href"))
         .filter(temp -> temp.startsWith("/url?q="))
         .forEach(temp -> {
            matcher = DOMAIN_NAME_PATTERN.matcher(temp);
            while (matcher.find()) {
               try {
                  String urlString = matcher.group(0).toLowerCase().trim();
                  URL url = new URL("http", urlString, 80, "");
                  log.info("web crawling - url={}", url);
                  searchResults.add(url);
               } catch (IOException e) {
                  throw new UncheckedIOException(e);
               }
            }
            log.info("number of sites crawled, searchResultsSize={}", searchResults.size());
         });
      log.info("searchResults={}", searchResults);
      return searchResults;
   }

   /**
    * @param urls This method downloads the URL content and adds to a list of Strings.
    *             Exception handling takes care of catching exception and continuing with
    *             next URL processing.
    * @return     List<String> having the content of the webpages
    */
   private CompletableFuture<List<String>> downloadPages(Set<URL> urls) {
      return CompletableFuture.supplyAsync(() -> {
         String content = null;
         for (URL url : urls) {
            try {
               content = new String(url.openStream().readAllBytes(),
                  StandardCharsets.UTF_8);
            } catch (UnknownHostException e) {
               log.info("UnknownHostException, msg={}, cause={}"
                  , e.getMessage(), e.getCause());
            } catch (IOException e) {
               e.printStackTrace();
            }
            downloadedPageContent.add(content);
         }
         return downloadedPageContent;
      }, executor);
   }

   /**
    * @param downloadPages This method takes list of downloadPages and finds the libraries used in them.
    *                      This is mocked at the moment to return fixed list of popular JS libraries.
    * @return List<String> Libraries popular in the downloadedPages
    */
   private List<String> getLibraries(List<String> downloadPages) {
      List<String> libraries = new ArrayList<>();
      downloadPages.forEach(page -> {
         List<String> libraryList = Jsoup.parse(page)
            .select("script")
            .stream()
            .map(element -> element.attr("src"))
            .filter(src -> !StringUtil.isBlank(src))
            .collect(Collectors.toList());
         libraries.addAll(libraryList);
      });
      // mocking the popular libraries to return
      return Arrays.asList("React Native", "Material UI", "Angular", "React", "Node.js");
   }

   private int threadsForComputation() {
      int availableProcessors = Runtime.getRuntime().availableProcessors();
      int nThreads = availableProcessors - 1;
      log.info("nThreads={}", nThreads);
      return nThreads;
   }
}
