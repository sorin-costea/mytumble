var myTumble = angular.module('myTumble', [ 'ngRoute' ]).config(
        [ '$routeProvider', function($routeProvider) {
            $routeProvider.when('/', {
                templateUrl : '/tpl/users.html',
                controller : 'Users'
            }).otherwise('/');
        } ]);

myTumble.factory('MyBackend', [ '$http', function($http) {
    var MyBackend = {};
    MyBackend.getUsers = function(filter) {
        return $http.get('/api/users' + filter);
    };
    MyBackend.updateUser = function(user) {
        return $http.put('/api/users/' + user.name, user);
    };
    return MyBackend;
} ]);

myTumble.controller('Users', [ '$scope', 'MyBackend', function($scope, MyBackend) {
    MyBackend.getUsers('?filter=followme,notbad').success(function(data) {
        $scope.users = data;
    });

    $scope.updateUser = function(workUser) {
        MyBackend.updateUser(workUser).then(function(response) {
            $scope.status = 'Updated user!';
        }, function(error) {
            $scope.status = 'Unable to update user: ' + error.message;
        });
    };
} ]);

myTumble.controller('Specials', [ '$scope', 'MyBackend', function($scope, MyBackend) {
    MyBackend.getUsers('?filter=special').success(function(data) {
        $scope.users = data;
    });
} ]);

myTumble.controller('Weirdos', [ '$scope', 'MyBackend', function($scope, MyBackend) {
    MyBackend.getUsers('?filter=weird').success(function(data) {
        $scope.users = data;
    });
} ]);

myTumble.controller('LastLikers', [ '$scope', 'MyBackend', function($scope, MyBackend) {
    MyBackend.getUsers('?filter=lastlikers,notweird').success(function(data) {
        $scope.users = data;
    });
} ]);

myTumble.controller('NotFollowMe', [ '$scope', 'MyBackend', function($scope, MyBackend) {
    MyBackend.getUsers('?filter=notspecial,notfollowme,ifollow').success(function(data) {
        $scope.users = data;
    });
} ]);