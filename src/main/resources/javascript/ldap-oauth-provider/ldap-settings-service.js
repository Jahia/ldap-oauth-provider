(function() {
    'use strict';

    angular.module('JahiaOAuthApp').service('ldapSettingsService', ldapSettingsService);

    ldapSettingsService.$inject = ['$http', 'jahiaContext'];

    function ldapSettingsService($http, jahiaContext) {
        return {
            getLdapProviderKey: getLdapProviderKey,
            setLdapProviderKey: setLdapProviderKey
        };
        function getLdapProviderKey(data) {
            data.action = 'getLdapProviderKey';
            return $http({
                method: 'POST',
                url: jahiaContext.baseEdit + jahiaContext.sitePath + '.manageLdapProviderKeysAction.do',
                params: data
            });
        }

        function setLdapProviderKey(data) {
            data.action = 'setLdapProviderKey';
            return $http({
                method: 'POST',
                url: jahiaContext.baseEdit + jahiaContext.sitePath + '.manageLdapProviderKeysAction.do',
                params: data
            });
        }
    }
})();