package main;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Shop {

    private static final Random random = new Random();
    private final String name;

    public Shop(String name) {
        this.name = name;
    }

    private static Executor getExecutor(int size) {
        // limit 100 in order to avoid a server crash
        return Executors.newFixedThreadPool(Math.min(size, 100), r -> {
            Thread t = new Thread(r);
            // Use daemon threads - they don't prevent the termination of the program
            t.setDaemon(true);
            return t;
        });
    }

    public static List<String> findPrices(List<Shop> shops, String product, Executor executor) {
        List<CompletableFuture<String>> priceFutures = shops.stream().map(
                shop ->
                        CompletableFuture.supplyAsync(
                                () -> shop.getName() + " price is " + shop.getPrice(product), executor))
                .collect(Collectors.toList());
        return priceFutures.stream().map(CompletableFuture::join).collect(Collectors.toList());
    }

    public static List<String> findPricesWithDiscount(List<Shop> shops, String product, Executor executor) {
        List<CompletableFuture<String>> priceFutures = shops.stream().map(
                shop ->
                        CompletableFuture.supplyAsync(
                                () -> shop.getQuote(product), executor))
                .map(future -> future.thenApply(Quote::parse))
                .map(future -> future.thenCompose(
                        quote ->
                            CompletableFuture.supplyAsync(
                                    () -> Discount.applyDiscount(quote), executor)))
                .collect(Collectors.toList());
        return priceFutures.stream().map(CompletableFuture::join).collect(Collectors.toList());
    }

    public static Stream<CompletableFuture<String>> findPricesWithDiscountStreamWithRandomDelay(List<Shop> shops, String product, Executor executor) {
        return shops.stream().map(
                shop ->
                        CompletableFuture.supplyAsync(
                                () -> shop.getQuoteWithRandomDelay(product), executor))
                .map(future -> future.thenApply(Quote::parse))
                .map(future -> future.thenCompose(
                        quote ->
                                CompletableFuture.supplyAsync(
                                        () -> Discount.applyDiscount(quote), executor)));
    }

    public String getName() {
        return name;
    }

    public Double getPrice(String product) {
        return Double.parseDouble(String.format("%.2f", calculatePrice(product)));
    }

    public String getQuote(String product) {
        double price = calculatePrice(product);
        Discount.Code code = Discount.Code.values()[random.nextInt(Discount.Code.values().length)];
        return String.format("%s:%.2f:%s", name, price, code);
    }

    public String getQuoteWithRandomDelay(String product) {
        double price = calculatePriceWithRandomDelay(product);
        Discount.Code code = Discount.Code.values()[random.nextInt(Discount.Code.values().length)];
        return String.format("%s:%.2f:%s", name, price, code);
    }

    // asynchronous API
    public CompletableFuture<Double> getPriceAsync(String product) {
        return CompletableFuture.supplyAsync(() -> calculatePrice(product));
    }

    public CompletableFuture<Double> getPriceInCurrencyAsync(Money.Currency currency, String product, Executor executor) {
        return CompletableFuture.supplyAsync(() -> getPrice(product), executor)
                .thenCombine(
                        CompletableFuture.supplyAsync(() -> Money.getRate(currency), executor),
                        (price, rate) -> price / rate);
    }

    private double calculatePrice(String product) {
        try {
            Thread.sleep(1000L);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return random.nextDouble() * product.charAt(0) + product.charAt(1);
    }

    private double calculatePriceWithRandomDelay(String product) {
        int delay = 500 + random.nextInt(2000);
        try {
            Thread.sleep(delay);

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return random.nextDouble() * product.charAt(0) + product.charAt(1);
    }

    public static void main(String[] args) {
        // Test1: using an asynchronous API
        System.out.println("Test1");
        Shop shop = new Shop("BestShop");
        long start1 = System.nanoTime();
        CompletableFuture<Double> futurePrice = shop.getPriceAsync("my favorite product");
        long invocationTime = ((System.nanoTime() - start1) / 1_000_000);
        System.out.println("Invocation returned after " + invocationTime + " msecs");

        try {
            double price = futurePrice.get();
            System.out.printf("Price is %.2f%n", price);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        long retrievalTime = ((System.nanoTime() - start1) / 1_000_000);
        System.out.println("Price returned after " + retrievalTime + " msecs");

        // Test2: non-blocking requests
        System.out.println("Test2");
        List<Shop> shops = Arrays.asList(
                new Shop("BestPrice"),
                new Shop("LetsSaveBig"),
                new Shop("MyFavoriteShop"),
                new Shop("BuyItAll"));
        final Executor executor = getExecutor(shops.size());
        long start2 = System.nanoTime();
        System.out.println(findPrices(shops, "myPhone", executor));
        long duration2 = (System.nanoTime() - start2) / 1_000_000;
        System.out.println("Done in " + duration2 + " msecs");
        // System.out.println(Runtime.getRuntime().availableProcessors()); - 12

        // Test3: composing sync and async operations
        System.out.println("Test3");
        long start3 = System.nanoTime();
        System.out.println(findPricesWithDiscount(shops, "myPhone", executor));
        long duration3 = (System.nanoTime() - start3) / 1_000_000;
        System.out.println("Done in " + duration3 + " msecs");

        // Test4: combining two independent CompletableFutures
        System.out.println("Test4");
        List<Executor> executors = Arrays.asList(ForkJoinPool.commonPool(), getExecutor(1));
        for (Executor e : executors) {
            long start4 = System.nanoTime();
            CompletableFuture<Double> futurePriceInCurrency = shop.getPriceInCurrencyAsync(Money.Currency.EUR, "my favorite product", e);

            try {
                double price = futurePriceInCurrency.get();
                System.out.printf("Price is %.2f in %s%n", price, Money.Currency.EUR);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            long duration4 = ((System.nanoTime() - start4) / 1_000_000);
            System.out.println("Done in " + duration4 + " msecs");
        }

        // Test5: reacting to a completableFuture completion
        System.out.println("Test5");
        long start5 = System.nanoTime();
        CompletableFuture[] futures = findPricesWithDiscountStreamWithRandomDelay(shops,"myPhone", executor)
                .map(f -> f.thenAccept(
                        s -> System.out.println(s + " (done in " + ((System.nanoTime() - start5) / 1_000_000) + " msecs)")))
                .toArray(size -> new CompletableFuture[size]);
        CompletableFuture.allOf(futures).join();
        System.out.println("All shops have now responded in " + ((System.nanoTime() - start5) / 1_000_000) + " msecs");
    }
}
