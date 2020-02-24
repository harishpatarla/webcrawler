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
   //private ExecutorService executor = Executors.newFixedThreadPool(threadsForComputation());
   private final ExecutorService executor = Executors.newCachedThreadPool();

   public void webCrawler(String searchTerm) {
      CompletableFuture.completedFuture(searchTerm)
         .thenComposeAsync(this::getDataFromGoogle, executor)
         .thenApply(this::extractLinks)
         .thenCompose(this::downloadPages)
         //.exceptionally(ex -> Collections.emptyList())
         .thenAccept(this::getLibraries);
   }

   private CompletableFuture<Document> getDataFromGoogle(String query) {
      return CompletableFuture.supplyAsync(() -> {
         String request = "https://www.google.com/search?q=" + query + "&num=50000";
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

   private Set<URL> extractLinks(Document doc) {
      Set<URL> searchResults = new HashSet<>();
      Elements links = doc.select("a[href]");
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
