package com.ke.bella.openapi.login.cas;

import com.ke.bella.openapi.Operator;
import com.ke.bella.openapi.login.session.SessionManager;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jasig.cas.client.validation.Assertion;
import org.jasig.cas.client.validation.Cas30ProxyReceivingTicketValidationFilter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.List;

import static com.ke.bella.openapi.login.config.BellaLoginConfiguration.redirectParameter;

public class BellaValidatorFilter extends Cas30ProxyReceivingTicketValidationFilter {
    private final boolean useCasUserId;
    private final String idAttribute;
    private final String nameAttribute;
    private final String emailAttribute;
    private final String source;
    private final List<String> optionalAttributes;
    private final SessionManager sessionManager;

    public BellaValidatorFilter(boolean useCasUserId, String idAttribute, String nameAttribute, String emailAttribute,
            String source, List<String> optionalAttributes, SessionManager sessionManager) {
        this.useCasUserId = useCasUserId;
        this.idAttribute = idAttribute;
        this.nameAttribute = nameAttribute;
        this.emailAttribute = emailAttribute;
        this.source = source;
        this.optionalAttributes = optionalAttributes;
        this.sessionManager = sessionManager;
    }

    @Override
    protected void onSuccessfulValidation(final HttpServletRequest request, final HttpServletResponse response,
            final Assertion assertion) {
        Operator operator = new Operator();
        if(useCasUserId) {
            operator.setUserId(Long.parseLong(assertion.getPrincipal().getAttributes().get(idAttribute).toString()));
        }
        operator.setUserName(assertion.getPrincipal().getAttributes().get(nameAttribute).toString());
        operator.setEmail(assertion.getPrincipal().getAttributes().get(emailAttribute).toString());
        operator.setSource(source);
        operator.setSourceId(assertion.getPrincipal().getAttributes().get(idAttribute).toString());
        if(CollectionUtils.isNotEmpty(optionalAttributes)) {
            for (String attributeName : optionalAttributes) {
                if(assertion.getPrincipal().getAttributes().containsKey(attributeName)) {
                    operator.getOptionalInfo().put(attributeName, assertion.getPrincipal().getAttributes().get(attributeName));
                }
            }
        }
        sessionManager.create(operator, request, response);
        if(StringUtils.isNotBlank(request.getParameter(redirectParameter))) {
            request.setAttribute(redirectParameter, request.getParameter(redirectParameter));
        }
    }
}
