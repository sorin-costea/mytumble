var myTumble = angular.module('myTumble', [ 'ui.router' ]).config(
        [ '$stateProvider', '$urlRouterProvider', function($stateProvider, $urlRouterProvider) {
            $urlRouterProvider.otherwise('/');
            $stateProvider.state('home', {
                url : '/',
                templateUrl : '/tpl/users.html',
                controller : 'Followers'
            }).state('lastlikers', {
                url : '/lastlikers',
                templateUrl : '/tpl/users.html',
                controller : 'LastLikers'
            }).state('baddies', {
                url : '/baddies',
                templateUrl : '/tpl/users.html',
                controller : 'Baddies'
            }).state('specials', {
                url : '/specials',
                templateUrl : '/tpl/users.html',
                controller : 'Specials'
            }).state('notfollowme', {
                url : '/notfollowme',
                templateUrl : '/tpl/users.html',
                controller : 'NotFollowMe'
            });
        } ]);

myTumble.factory('MyBackend', [ '$http', function($http) {
    var MyBackend = {};
    MyBackend.getUsers = function(filter) {
        return $http.get('/api/users' + filter);
    };
    return MyBackend;
} ]);

myTumble.controller('Followers', [ '$scope', 'MyBackend', function($scope, MyBackend) {
    MyBackend.getUsers('?filter=followme,notbad').success(function(data) {
        $scope.users = data;
    });
} ]);

myTumble.controller('Specials', [ '$scope', 'MyBackend', function($scope, MyBackend) {
    MyBackend.getUsers('?filter=special').success(function(data) {
        $scope.users = data;
    });
} ]);

myTumble.controller('Baddies', [ '$scope', 'MyBackend', function($scope, MyBackend) {
    MyBackend.getUsers('?filter=bad').success(function(data) {
        $scope.users = data;
    });
} ]);

myTumble.controller('LastLikers', [ '$scope', 'MyBackend', function($scope, MyBackend) {
    MyBackend.getUsers('?filter=lastlikers,notbad').success(function(data) {
        $scope.users = data;
    });
} ]);

myTumble.controller('NotFollowMe', [ '$scope', 'MyBackend', function($scope, MyBackend) {
    MyBackend.getUsers('?filter=notspecial,notfollowme,ifollow').success(function(data) {
        $scope.users = data;
    });
} ]);