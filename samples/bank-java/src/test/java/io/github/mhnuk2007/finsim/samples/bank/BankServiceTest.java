package io.github.mhnuk2007.finsim.samples.bank;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mhnuk2007.finsim.samples.bank.BankService.Outcome;
import io.github.mhnuk2007.finsim.samples.bank.Contract.Direction;
import io.github.mhnuk2007.finsim.samples.bank.Contract.ErrorBody;
import io.github.mhnuk2007.finsim.samples.bank.Contract.LegStatus;
import io.github.mhnuk2007.finsim.samples.bank.Contract.MoneyDto;
import io.github.mhnuk2007.finsim.samples.bank.Contract.PaymentLegRequest;
import io.github.mhnuk2007.finsim.samples.bank.Contract.PaymentLegResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BankServiceTest {

  private final BankService bank =
      new BankService(
          new BankProperties(
              "bank-a",
              List.of("USD"),
              Map.of(
                  "ACC-1", new BigDecimal("1000.00"),
                  "ACC-2", new BigDecimal("250.00"),
                  "ACC-CLOSED", new BigDecimal("0.00")),
              Set.of("ACC-CLOSED")));

  private static PaymentLegRequest leg(String tx, Direction d, String account, String amount) {
    return new PaymentLegRequest(
        tx, d, account, new MoneyDto(amount, "USD"), "TEST", null, "SYNTHETIC");
  }

  private static PaymentLegRequest reversal(
      String tx, String reversalOf, String account, String amount) {
    return new PaymentLegRequest(
        tx,
        Direction.CREDIT,
        account,
        new MoneyDto(amount, "USD"),
        "TEST",
        reversalOf,
        "SYNTHETIC");
  }

  private static String code(Outcome o) {
    return ((ErrorBody) o.body()).code();
  }

  @Test
  void debitReducesBalanceAndReportsCompleted() {
    Outcome o = bank.applyLeg("key-0001-a", leg("tx-1", Direction.DEBIT, "ACC-1", "100.00"));

    assertThat(o.status()).isEqualTo(201);
    PaymentLegResult result = (PaymentLegResult) o.body();
    assertThat(result.status()).isEqualTo(LegStatus.COMPLETED);
    assertThat(result.provenance()).isEqualTo("SYNTHETIC");
    assertThat(bank.balanceOf("ACC-1")).isEqualByComparingTo("900.00");
  }

  @Test
  void creditIncreasesBalance() {
    bank.applyLeg("key-0002-a", leg("tx-2", Direction.CREDIT, "ACC-2", "50.00"));

    assertThat(bank.balanceOf("ACC-2")).isEqualByComparingTo("300.00");
  }

  @Test
  void insufficientFundsIsRejectedWithAm04AndChangesNothing() {
    Outcome o = bank.applyLeg("key-0003-a", leg("tx-3", Direction.DEBIT, "ACC-1", "5000.00"));

    assertThat(o.status()).isEqualTo(422);
    assertThat(code(o)).isEqualTo("AM04");
    assertThat(bank.balanceOf("ACC-1")).isEqualByComparingTo("1000.00");
  }

  @Test
  void unknownAccountClosedAccountCurrencyAndAmountAreRejectedWithTheRightCodes() {
    assertThat(code(bank.applyLeg("key-0004-a", leg("tx-4a", Direction.CREDIT, "NOPE", "1.00"))))
        .isEqualTo("AC01");
    assertThat(
            code(bank.applyLeg("key-0004-b", leg("tx-4b", Direction.CREDIT, "ACC-CLOSED", "1.00"))))
        .isEqualTo("AC04");
    assertThat(
            code(
                bank.applyLeg(
                    "key-0004-c",
                    new PaymentLegRequest(
                        "tx-4c",
                        Direction.CREDIT,
                        "ACC-1",
                        new MoneyDto("1.00", "EUR"),
                        "TEST",
                        null,
                        "SYNTHETIC"))))
        .isEqualTo("AM03");
    assertThat(code(bank.applyLeg("key-0004-d", leg("tx-4d", Direction.CREDIT, "ACC-1", "0.00"))))
        .isEqualTo("AM02");
  }

  @Test
  void replayWithSameKeyAndBodyReturnsOriginalAndAppliesOnce() {
    PaymentLegRequest request = leg("tx-5", Direction.DEBIT, "ACC-1", "100.00");

    Outcome first = bank.applyLeg("key-0005-a", request);
    Outcome replay = bank.applyLeg("key-0005-a", request);

    assertThat(first.status()).isEqualTo(201);
    assertThat(replay.status()).isEqualTo(200);
    assertThat(replay.body()).isEqualTo(first.body());
    assertThat(bank.balanceOf("ACC-1")).isEqualByComparingTo("900.00");
  }

  @Test
  void replayOfARejectionReturnsTheSameRejection() {
    PaymentLegRequest request = leg("tx-6", Direction.DEBIT, "ACC-1", "5000.00");

    Outcome first = bank.applyLeg("key-0006-a", request);
    Outcome replay = bank.applyLeg("key-0006-a", request);

    assertThat(replay.status()).isEqualTo(422);
    assertThat(replay.body()).isEqualTo(first.body());
  }

  @Test
  void sameKeyWithDifferentBodyIsAConflict() {
    bank.applyLeg("key-0007-a", leg("tx-7", Direction.DEBIT, "ACC-1", "100.00"));

    Outcome o = bank.applyLeg("key-0007-a", leg("tx-7", Direction.DEBIT, "ACC-1", "200.00"));

    assertThat(o.status()).isEqualTo(409);
    assertThat(code(o)).isEqualTo("IDEMPOTENCY_CONFLICT");
    assertThat(bank.balanceOf("ACC-1")).isEqualByComparingTo("900.00");
  }

  @Test
  void missingOrShortKeyIsABadRequest() {
    PaymentLegRequest request = leg("tx-8", Direction.DEBIT, "ACC-1", "1.00");

    assertThat(bank.applyLeg(null, request).status()).isEqualTo(400);
    assertThat(bank.applyLeg("short", request).status()).isEqualTo(400);
  }

  @Test
  void nonSyntheticProvenanceIsABadRequest() {
    PaymentLegRequest request =
        new PaymentLegRequest(
            "tx-9", Direction.DEBIT, "ACC-1", new MoneyDto("1.00", "USD"), "TEST", null, "REAL");

    assertThat(bank.applyLeg("key-0009-a", request).status()).isEqualTo(400);
  }

  @Test
  void statusQueryFindsAppliedAndRejectedLegsAndNotFoundOtherwise() {
    bank.applyLeg("key-0010-a", leg("tx-10", Direction.DEBIT, "ACC-1", "10.00"));
    bank.applyLeg("key-0010-b", leg("tx-11", Direction.DEBIT, "ACC-1", "9999.00"));

    assertThat(bank.status("tx-10", Direction.DEBIT))
        .get()
        .extracting(PaymentLegResult::status)
        .isEqualTo(LegStatus.COMPLETED);
    assertThat(bank.status("tx-11", Direction.DEBIT))
        .get()
        .extracting(PaymentLegResult::reasonCode)
        .isEqualTo("AM04");
    assertThat(bank.status("never-sent", Direction.CREDIT)).isEmpty();
    assertThat(bank.status("tx-10", Direction.CREDIT)).isEmpty();
  }

  @Test
  void reversalRestoresTheBalanceExactlyOnce() {
    bank.applyLeg("key-0012-a", leg("tx-12", Direction.DEBIT, "ACC-1", "100.00"));

    Outcome first = bank.applyLeg("key-0012-b", reversal("tx-12-rev", "tx-12", "ACC-1", "100.00"));
    Outcome second =
        bank.applyLeg("key-0012-c", reversal("tx-12-rev2", "tx-12", "ACC-1", "100.00"));

    assertThat(first.status()).isEqualTo(201);
    assertThat(second.status()).isEqualTo(422);
    assertThat(code(second)).isEqualTo("DUPL");
    assertThat(bank.balanceOf("ACC-1")).isEqualByComparingTo("1000.00");
  }

  @Test
  void reversalOfAnUnknownDebitIsRejected() {
    Outcome o =
        bank.applyLeg("key-0013-a", reversal("tx-13-rev", "no-such-debit", "ACC-1", "10.00"));

    assertThat(o.status()).isEqualTo(422);
    assertThat(code(o)).isEqualTo("MS03");
    assertThat(bank.balanceOf("ACC-1")).isEqualByComparingTo("1000.00");
  }

  @Test
  void totalMoneyIsConservedAcrossATransferBetweenAccounts() {
    BigDecimal before = bank.balanceOf("ACC-1").add(bank.balanceOf("ACC-2"));

    bank.applyLeg("key-0014-a", leg("tx-14", Direction.DEBIT, "ACC-1", "123.45"));
    bank.applyLeg("key-0014-b", leg("tx-14", Direction.CREDIT, "ACC-2", "123.45"));

    assertThat(bank.balanceOf("ACC-1").add(bank.balanceOf("ACC-2"))).isEqualByComparingTo(before);
  }
}
