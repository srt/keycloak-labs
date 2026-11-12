<#import "template.ftl" as layout>
<#import "field.ftl" as field>
<#import "buttons.ftl" as buttons>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('birthdate'); section>
<!-- template: login-update-birthdate.ftl (Übung Required Action) -->
    <#if section="header">
        ${msg("updateBirthdateTitle")}
    <#elseif section="form">
        <form id="kc-update-birthdate-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <@field.input name="birthdate" label=msg("birthdateLabel") autocomplete="bday" autofocus=true />
            <@buttons.actionGroup>
                <@buttons.button id="kc-submit" label="doSubmit"/>
            </@buttons.actionGroup>
        </form>
    </#if>
</@layout.registrationLayout>
