package com.modernize.bankbatch.processor;

import com.modernize.bankbatch.exception.ValidationException;
import com.modernize.bankbatch.model.StagedTransaction;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
public class ValidationProcessor implements ItemProcessor<StagedTransaction, StagedTransaction> {

    @Override
    public StagedTransaction process(StagedTransaction item) {
        StringBuilder errors = new StringBuilder();

        // Rule 1: positive amount
        if (item.getAmountCents() <= 0) {
            errors.append("amount must be positive; ");
        }

        // Rule 2: valid direction
        if (!"D".equals(item.getDirection()) && !"C".equals(item.getDirection())) {
            errors.append("direction must be D or C; ");
        }

        // Rule 3: account exists
        if (item.getAccountStatus() == null) {
            errors.append("account not found; ");
        }

        // Rule 4: account is active
        if (item.getAccountStatus() != null && !"active".equals(item.getAccountStatus())) {
            errors.append("account is not active; ");
        }

        if (errors.length() > 0) {
            item.setStatus("rejected");
            item.setErrorMessage(errors.toString());
            throw new ValidationException(item.getId(), errors.toString());
        }

        item.setStatus("validated");
        return item;
    }
}
