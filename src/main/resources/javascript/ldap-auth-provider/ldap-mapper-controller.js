(function () {
    'use strict';

    angular.module('JahiaOAuthApp').controller('LdapOAuthProviderController', LdapOAuthController);

    LdapOAuthController.$inject = ['$routeParams', 'settingsService', 'helperService', 'i18nService'];

    function LdapOAuthController($routeParams, settingsService, helperService, i18nService) {
        var vm = this;

        // Variables
        vm.enabled = false;
        vm.connectorProperties = [];
        vm.mapperProperties = [];
        vm.mapping = [];
        vm.selectedPropertyFromConnector = '';
        vm.ldapOAuth_providerKey = '';
        vm.selectedPropertyFromMapper = '';
        vm.expandedCard = false;

        // Functions
        vm.saveMapperSettings = saveMapperSettings;
        vm.addMapping = addMapping;
        vm.removeMapping = removeMapping;
        vm.getConnectorI18n = getConnectorI18n;
        vm.getMapperI18n = getMapperI18n;
        vm.toggleCard = toggleCard;
        vm.orderByConnector = orderByConnector;
        vm.orderByMapper = orderByMapper;
        
        _init();

        function saveMapperSettings() {
            var isMappingComplete = true;
            angular.forEach(vm.mapping, function (mapped) {
                if (!mapped.mapper || !mapped.connector) {
                    isMappingComplete = false;
                }
            });
            if (!isMappingComplete) {
                helperService.errorToast(i18nService.message('joant_ldapOAuthView.message.error.incompleteMapping'));
                return false;
            }

            var mandatoryPropertyAreMapped = true;
            angular.forEach(vm.mapperProperties, function (property) {
                if (property.mandatory) {
                    if (_isNotMapped(property.name, 'mapper')) {
                        mandatoryPropertyAreMapped = false
                    }
                }
            });
            if (vm.enabled && !mandatoryPropertyAreMapped) {
                helperService.errorToast(i18nService.message('joant_ldapOAuthView.message.error.mandatoryPropertiesNotMapped'));
                return false;
            }

            settingsService.setMapperMapping({
                connectorServiceName: $routeParams.connectorServiceName,
                mapperServiceName: 'ldapOAuthProvider',
                properties: {
                    enabled: vm.enabled,
                    ldapOAuth_providerKey: vm.ldapOAuth_providerKey,
                    ldapOAuth_userBaseDn: vm.ldapOAuth_userBaseDn,
                    ldapOAuth_rdn: vm.ldapOAuth_rdn,
                    ldapOAuth_objectClass: vm.ldapOAuth_objectClass,
                    ldapOAuth_static_properties: vm.ldapOAuth_static_properties
                },
                mapping: vm.mapping
            }).success(function () {
                helperService.successToast(i18nService.message('joant_ldapOAuthView.message.success.mappingSaved'));
            }).error(function (data) {
                helperService.errorToast(i18nService.message('joant_ldapOAuthView.message.label') + ' ' + data.error);
            });
        }

        function addMapping() {
            if (vm.selectedPropertyFromConnector) {
                vm.mapping.push({
                    connector: vm.selectedPropertyFromConnector
                });
                vm.selectedPropertyFromConnector = '';
            }
        }

        function removeMapping(index) {
            vm.mapping.splice(index, 1);
        }

        function getConnectorI18n(value) {
            return i18nService.message($routeParams.connectorServiceName + '.label.' + value);
        }

        function getMapperI18n(value) {
            return i18nService.message('ldapOAuthProvider.label.' + value.replace(':', '_'));
        }

        function toggleCard() {
            vm.expandedCard = !vm.expandedCard;
        }

        function orderByConnector(property) {
            return getConnectorI18n(property.name);
        }

        function orderByMapper(property) {
            return getMapperI18n(property.name);
        }

        // Private functions under this line
        function _init() {
            i18nService.addKey(ldapoai18n);

            settingsService.getMapperMapping({
                connectorServiceName: $routeParams.connectorServiceName,
                mapperServiceName: 'ldapOAuthProvider',
                properties: ['ldapOAuth_providerKey', 'ldapOAuth_userBaseDn', 'ldapOAuth_rdn', 'ldapOAuth_objectClass', 'ldapOAuth_static_properties']
            }).success(function (data) {
                if (!angular.equals(data, {})) {
                    vm.enabled = data.enabled;
                    vm.mapping = data.mapping;
                    vm.ldapOAuth_providerKey = data.ldapOAuth_providerKey;
                    vm.ldapOAuth_userBaseDn = data.ldapOAuth_userBaseDn;
                    vm.ldapOAuth_rdn = data.ldapOAuth_rdn;
                    vm.ldapOAuth_objectClass = data.ldapOAuth_objectClass;
                    vm.ldapOAuth_static_properties = data.ldapOAuth_static_properties;
                }
            }).error(function (data) {
                helperService.errorToast(i18nService.message('joant_ldapOAuthView.message.label') + ' ' + data.error);
            });

            settingsService.getConnectorProperties({
                connectorServiceName: $routeParams.connectorServiceName
            }).success(function (data) {
                vm.connectorProperties = data.connectorProperties;
            }).error(function (data) {
                helperService.errorToast(i18nService.message('joant_ldapOAuthView.message.label') + ' ' + data.error);
            });

            settingsService.getMapperProperties({
                mapperServiceName: 'ldapOAuthProvider'
            }).success(function (data) {
                vm.mapperProperties = data.mapperProperties;
            }).error(function (data) {
                helperService.errorToast(i18nService.message('joant_ldapOAuthView.message.label') + ' ' + data.error);
            });
        }

        function _isNotMapped(field, key) {
            var isNotMapped = true;
            angular.forEach(vm.mapping, function (entry) {
                if (entry[key] && entry[key].name == field) {
                    isNotMapped = false;
                }
            });
            return isNotMapped;
        }
    }
})();