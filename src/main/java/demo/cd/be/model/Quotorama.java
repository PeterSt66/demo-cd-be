package demo.cd.be.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Component;

@Component
public class Quotorama {
    private static final int VERY_BAD_WAY_OF_DOING_THIS = 1000;

    private Random random = new Random();

    private List<String[]> quotes = new ArrayList<>();

    public Quotorama() {
        try {
            String[] quote;
            String line;
            try (InputStream res = Quotorama.class.getResourceAsStream("/quotes.sv")) {
                BufferedReader br = new BufferedReader(new InputStreamReader(res));
                while ((line = br.readLine()) != null) {
                    quote = line.split(":");
                    if (quote.length > 1) {
                        quotes.add(quote);
                        // System.out.println("@@Q:"+quote[0]+"#"+quote[1]+"#"+quote[2]+"@@");
                    }
                }
            }
        } catch (IOException ie) {
            throw new IllegalStateException(ie);
        }
    }

    public String[] nextQuote() {
        int ni = random.nextInt(quotes.size());
        return quotes.get(ni);
    }

    public String[] nextQuote(String group) {
        String[] quote = nextQuote();
        for (int i = 0; i < VERY_BAD_WAY_OF_DOING_THIS; i++) {
            if (group.equals(quote[0])) {
                break;
            }
            quote = nextQuote();
        }
        return quote;
    }

}
