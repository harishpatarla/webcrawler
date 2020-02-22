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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
   private static final String DOMAIN_NAME_PATTERN
      = "([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,6}";
   //private ExecutorService executor = Executors.newFixedThreadPool(threadsForComputation());
   private final ExecutorService executor = Executors.newCachedThreadPool();
   private Matcher matcher;

   private static final Pattern patternDomainName;

   public void webCrawler(String searchTerm) {

      // List<String> popularLibraries = Arrays.asList("React Native", "Material UI", "Angular", "React", "Node.js");

      CompletableFuture.completedFuture(searchTerm)
         .thenComposeAsync(this::getDataFromGoogle, executor)
         .thenApply(this::extractLinks)
         .thenCompose(this::downloadPages)
         .thenAccept(this::getLibraries);
   }

   private CompletableFuture<Document> getDataFromGoogle(String query) {
      return CompletableFuture.supplyAsync(() -> {
         String request = "https://www.google.com/search?q=" + query + "&num=20";
         log.info("Sending request={}", request);
         Document doc = null;
         try {
            doc = Jsoup
               .connect(request)
               .userAgent(
                  "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)")
               .timeout(100000).get();
         } catch (IOException e) {
            e.printStackTrace();
         }
         return doc;
      }, executor);
   }

   private Set<URL> extractLinks(Document doc) {
      log.info("extracting links for doc={}", doc);
      Set<URL> searchResults = new HashSet<>();
      Elements links = doc.select("a[href]");
      links.stream()
         .map(link -> link.attr("href"))
         .filter(temp -> temp.startsWith("/url?q="))
         .forEach(temp -> {
            matcher = patternDomainName.matcher(temp);
            while (matcher.find()) {
               try {
                  URL url = new URL(matcher.group(0).toLowerCase().trim());
                  searchResults.add(url);
               } catch (IOException e) {
                  throw new UncheckedIOException(e);
               }
            }
            /*if (matcher.find()) {
               String link = matcher.group(0).toLowerCase().trim();
               try {
                  searchResults.add(new URL(link));
               } catch (MalformedURLException e) {
                  e.printStackTrace();
               }
            }*/
         });
      log.info("searchResults={}", searchResults);
      return searchResults;
   }

   private CompletableFuture<List<String>> downloadPages(Set<URL> urls) {
      return CompletableFuture.supplyAsync(() -> {
         var downloadedPageContent = new ArrayList<String>();
         String content = null;
         try {
            for (URL url : urls) {
               content = new String(url.openStream().readAllBytes(),
                  StandardCharsets.UTF_8);
            }
         } catch (IOException e) {
            e.printStackTrace();
         }
         downloadedPageContent.add(content);
         log.info("downloadedPageContent={}", downloadedPageContent);
         return downloadedPageContent;
      }, executor);

   }

   private List<String> getLibraries(List<String> downloadPages) {
      List<String> libraries = new ArrayList<>();
      downloadPages.forEach(page -> {
         Jsoup.parse(page)
            .select("script")
            .stream()
            .map(element -> element.attr("src"))
            .filter(src -> !StringUtil.isBlank(src))
            .collect(Collectors.toList());
      });
      return libraries;
   }

   private int threadsForComputation() {
      int availableProcessors = Runtime.getRuntime().availableProcessors();
      int nThreads = availableProcessors - 2;
      log.info("nThreads={}", nThreads);
      return nThreads;
   }

   static {
      patternDomainName = Pattern.compile(DOMAIN_NAME_PATTERN);
   }
}
