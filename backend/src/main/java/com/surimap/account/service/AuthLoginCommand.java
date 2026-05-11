package com.surimap.account.service;

import com.surimap.common.auth.Channel;

public record AuthLoginCommand(
    String accountCode, String password, Channel channel, String policePhoneCode) {}
