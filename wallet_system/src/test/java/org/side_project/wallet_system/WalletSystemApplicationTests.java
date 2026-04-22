package org.side_project.wallet_system;

import com.stripe.Stripe;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Price;
import com.stripe.model.Product;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PriceCreateParams;
import com.stripe.param.ProductCreateParams;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WalletSystemApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    @Disabled("Requires real Stripe credentials — exploratory code only")
    void stripe_function_test() throws StripeException {
        // Don't embed any keys in production code. This is an example.
        // See https://docs.stripe.com/keys-best-practices.
        StripeClient stripeClient = new StripeClient("");

        ProductCreateParams productParams =
                ProductCreateParams.builder()
                        .setName("Starter Subscription")
                        .setDescription("$12/Month subscription")
                        .build();
        Product product = stripeClient.v1().products().create(productParams);
        System.out.println("Success! Here is your starter subscription product id: " + product.getId());

        PriceCreateParams params =
                PriceCreateParams
                        .builder()
                        .setProduct(product.getId())
                        .setCurrency("usd")
                        .setUnitAmount(1200L)
                        .setRecurring(
                                PriceCreateParams.Recurring
                                        .builder()
                                        .setInterval(PriceCreateParams.Recurring.Interval.MONTH)
                                        .build())
                        .build();
        Price price = stripeClient.v1().prices().create(params);
        System.out.println("Success! Here is your starter subscription price id: " + price.getId());
    }

    @Test
    @Disabled("Requires real Stripe credentials — exploratory code only")
    void stripe_paymentMethod_test() throws StripeException {
        // Don't put any keys in code. See https://docs.stripe.com/keys-best-practices.
// Find your keys at https://dashboard.stripe.com/apikeys.
        Stripe.apiKey = "";

        PaymentIntentCreateParams params =
                PaymentIntentCreateParams.builder()
                        .setAmount(1099L)
                        .setCurrency("usd")
                        .build();

        PaymentIntent paymentIntent = PaymentIntent.create(params);
        System.out.println(paymentIntent);
    }

    @Test
    @Disabled("Requires real Stripe credentials — exploratory code only")
    void stripe_paymentIntent_test() throws StripeException {
        // Don't put any keys in code. See https://docs.stripe.com/keys-best-practices.
// Find your keys at https://dashboard.stripe.com/apikeys.
        Stripe.apiKey = "";

        PaymentIntentCreateParams params =
                PaymentIntentCreateParams.builder().setAmount(1099L).setCurrency("usd").build();

        PaymentIntent paymentIntent = PaymentIntent.create(params);
        System.out.println(paymentIntent);
    }
}
