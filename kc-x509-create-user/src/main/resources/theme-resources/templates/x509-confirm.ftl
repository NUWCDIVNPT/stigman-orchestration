<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=true displayInfo=false; section>
    <#if section = "header">
        ${msg("x509ConfirmTitle", "Certificate Authentication")}
    <#elseif section = "form">
        <div id="kc-form">
            <div id="kc-form-wrapper">
                <div class="alert alert-info" style="margin-bottom: 20px;">
                    <#if isNewUser?? && isNewUser>
                        <h3>${msg("x509NewUserTitle", "Account Created")}</h3>
                        <p>${msg("x509NewUserMessage", "A new account has been created for you based on your certificate.")}</p>
                    <#else>
                        <h3>${msg("x509ExistingUserTitle", "Welcome Back")}</h3>
                        <p>${msg("x509ExistingUserMessage", "You are logging in with your certificate.")}</p>
                    </#if>
                </div>

                <div class="kc-form-group" style="margin-bottom: 20px; padding: 15px; background-color: #f5f5f5; border-radius: 4px;">
                    <p style="margin: 0;">
                        <strong>${msg("x509UsernameLabel", "Username")}:</strong> ${username!}
                    </p>
                </div>

                <form id="kc-x509-confirm-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
                    <div class="${properties.kcFormGroupClass!}">
                        <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
                            <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}"
                                   name="confirm"
                                   type="submit"
                                   value="${msg("x509ConfirmButton", "Continue")}" />
                        </div>
                    </div>
                </form>
            </div>
        </div>
    </#if>
</@layout.registrationLayout>
