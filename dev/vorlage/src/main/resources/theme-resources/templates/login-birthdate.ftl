<#import "template.ftl" as layout>
<#import "field.ftl" as field>
<#import "buttons.ftl" as buttons>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('birthdate'); section>
<!-- template: login-birthdate.ftl (Übung Authenticator) -->
    <#if section="header">
        ${msg("birthdateTitle")}
    <#elseif section="form">
        <form id="kc-birthdate-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <@field.input name="birthdate" label=msg("birthdateLabel") autocomplete="bday" autofocus=true />

            <#-- Widget liegt im Provider-JAR (theme-resources/resources) - kein externer Host, keine Cookies -->
            <div class="${properties.kcFormGroupClass!}">
                <altcha-widget challenge="${altchaChallenge}" name="altcha" language="${(locale.currentLanguageTag)!'de'}" auto="onload"></altcha-widget>
            </div>

            <@buttons.loginButton />
        </form>
        <script type="module" src="${url.resourcesPath}/altcha/altcha.i18n.min.js"></script>
    </#if>
</@layout.registrationLayout>
