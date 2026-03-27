package com.modernize.verificationlab.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One row from bank.transactions (or the baseline equivalent).
 *
 * Used in both ActualOutput (collected from the database) and BaselineOutput
 * (deserialized from baseline.json). Keeping them the same type means the
 * comparator works on identical structures.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransactionRow {

    private int accountId;
    private Integer merchantId;   // nullable — some transactions have no merchant
    private String direction;
    private long amountCents;
    private String description;

    public TransactionRow() {}

    public TransactionRow(int accountId, Integer merchantId, String direction,
                          long amountCents, String description) {
        this.accountId = accountId;
        this.merchantId = merchantId;
        this.direction = direction;
        this.amountCents = amountCents;
        this.description = description;
    }

    public int getAccountId()      { return accountId; }
    public Integer getMerchantId() { return merchantId; }
    public String getDirection()   { return direction; }
    public long getAmountCents()   { return amountCents; }
    public String getDescription() { return description; }

    public void setAccountId(int accountId)        { this.accountId = accountId; }
    public void setMerchantId(Integer merchantId)  { this.merchantId = merchantId; }
    public void setDirection(String direction)      { this.direction = direction; }
    public void setAmountCents(long amountCents)   { this.amountCents = amountCents; }
    public void setDescription(String description) { this.description = description; }
}
