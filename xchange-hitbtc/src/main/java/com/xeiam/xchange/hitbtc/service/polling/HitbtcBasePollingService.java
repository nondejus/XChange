package com.xeiam.xchange.hitbtc.service.polling;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.xeiam.xchange.ExchangeException;
import com.xeiam.xchange.FundsExceededException;
import com.xeiam.xchange.NonceException;
import com.xeiam.xchange.hitbtc.dto.HitbtcException;
import com.xeiam.xchange.hitbtc.dto.trade.HitbtcExecutionReport;
import si.mazi.rescu.ParamsDigest;
import si.mazi.rescu.RestProxyFactory;
import si.mazi.rescu.SynchronizedValueFactory;

import com.xeiam.xchange.ExchangeSpecification;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.hitbtc.Hitbtc;
import com.xeiam.xchange.hitbtc.HitbtcAdapters;
import com.xeiam.xchange.hitbtc.service.HitbtcHmacDigest;
import com.xeiam.xchange.service.BaseExchangeService;
import com.xeiam.xchange.service.polling.BasePollingService;

public abstract class HitbtcBasePollingService<T extends Hitbtc> extends BaseExchangeService implements BasePollingService {

  protected static final String HITBTC = "hitbtc";
  protected static final String HITBTC_ORDER_FEE_POLICY_MAKER = HITBTC + ".order.feePolicy.maker";
  protected static final String HITBTC_ORDER_FEE_LISTING_DEFAULT = HITBTC + ORDER_FEE_LISTING + "default";

  protected final SynchronizedValueFactory<Long> valueFactory;

  protected final T hitbtc;
  protected final String apiKey;
  protected final ParamsDigest signatureCreator;
  private final Set<CurrencyPair> currencyPairs;

  /**
   * Constructor Initialize common properties from the exchange specification
   * 
   * @param exchangeSpecification The {@link com.xeiam.xchange.ExchangeSpecification}
   */
  protected HitbtcBasePollingService(Class<T> hitbtcType, ExchangeSpecification exchangeSpecification, SynchronizedValueFactory<Long> nonceFactory) {

    super(exchangeSpecification);

    this.valueFactory = nonceFactory;
    this.hitbtc = RestProxyFactory.createProxy(hitbtcType, exchangeSpecification.getSslUri());
    this.apiKey = exchangeSpecification.getApiKey();
    String apiKey = exchangeSpecification.getSecretKey();
    this.signatureCreator = apiKey != null && !apiKey.isEmpty() ? HitbtcHmacDigest.createInstance(apiKey) : null;
    this.currencyPairs = new HashSet<CurrencyPair>();
  }

  @Override
  public synchronized Collection<CurrencyPair> getExchangeSymbols() throws IOException {

    if (currencyPairs.isEmpty()) {
      currencyPairs.addAll(HitbtcAdapters.adaptCurrencyPairs(hitbtc.getSymbols()));
    }

    return currencyPairs;
  }

  protected void checkRejected(HitbtcExecutionReport executionReport) {
    if ("rejected".equals(executionReport.getExecReportType())) {
      if ("orderExceedsLimit".equals(executionReport.getOrderRejectReason()))
        throw new FundsExceededException(executionReport.getClientOrderId());
      else if ("exchangeClosed ".equals(executionReport.getOrderRejectReason()))
        throw new IllegalStateException(executionReport.getOrderRejectReason());
      else
        throw new IllegalArgumentException("Order rejected, " + executionReport.getOrderRejectReason());
    }
  }

  protected RuntimeException handleException(HitbtcException exception) {
    String message = exception.getMessage();

    if ("Nonce has been used".equals(message))
      return new NonceException(message);
    return new ExchangeException(message);
  }
}
