package com.hypers.account.contract.account;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SsoTicketExchangeRequest {

    private String appCode;
    private String code;
}
