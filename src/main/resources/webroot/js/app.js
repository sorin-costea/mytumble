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
    MyBackend.likeLikers = function() {
        return $http.put('/api/status/likelikers');
    };
    MyBackend.unfollowAsocials = function() {
        return $http.put('/api/status/unfollowasocials');
    };
    MyBackend.refreshFollowers = function() {
        return $http.put('/api/status/refreshfollowers');
    };
    MyBackend.updateUser = function(user) {
        return $http.put('/api/users/' + user.name, user);
    };
    return MyBackend;
} ]);

myTumble.controller('Users', [ '$scope', 'MyBackend', function($scope, MyBackend) {
    $scope.userfilter = '';

    $scope.loadUsers = function(filter) {
        if(filter === $scope.userfilter)
            return;
        MyBackend.getUsers('?filter=' + filter).then(function(response) {
            $scope.userfilter = filter;
            $scope.users = response.data;
        }, function(error) {
            $scope.pageStatus = 'Failed to load users ('  + $scope.userFilter + '): ' + error.message;
        });
    };
    
    $scope.updateUser = function(workUser) {
        MyBackend.updateUser(workUser).then(function(response) {
            $scope.pageStatus = 'Updated user ' + workUser.name;
        }, function(error) {
            $scope.pageStatus = 'Unable to update user: ' + error.message;
        });
    };
    
    $scope.likeLikers = function() {
        MyBackend.likeLikers().then(function(response) {
            $scope.pageStatus = 'Liked users';
        }, function(error) {
            $scope.pageStatus = 'Failed to like users: ' + error.message;
        });
    };
    
    $scope.unfollowAsocials = function() {
        MyBackend.unfollowAsocials().then(function(response) {
            $scope.pageStatus = 'Unfollowed those who don\'t follow back';
        }, function(error) {
            $scope.pageStatus = 'Failed to unfollow users: ' + error.message;
        });
    };

    $scope.refreshFollowers = function() {
        MyBackend.refreshFollowers().then(function(response) {
            $scope.pageStatus = 'Refreshing the followers database';
        }, function(error) {
            $scope.pageStatus = 'Failed to refresh followers: ' + error.message;
        });
    };
    
    $scope.loadUsers('followme,notweird');
} ]);
