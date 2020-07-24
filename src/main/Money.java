package main;

public class Money {
    public enum Currency {
        USD(1),
        EUR(1.16);

        private double rate;

        Currency(double rate) {
            this.rate = rate;
        }
    }

    public static double getRate(Currency currency) {
        try {
            Thread.sleep(1000L);

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return currency.rate;
    }
}
