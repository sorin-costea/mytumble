var myTumble = angular.module('myTumble', []);

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

myTumble.controller('Users', [
        '$scope',
        'MyBackend',
        function($scope, MyBackend) {
            $scope.messages = [];
            $scope.userfilter = '';
            $scope.eb = null;

            $scope.addLog = function(text) {
                var message = {
                    "timestamp" : Date.now(),
                    "text" : text
                };
                $scope.messages.unshift(message);
            }

            $scope.loadUsers = function(filter) {
                if (filter === $scope.userfilter)
                    return;
                $scope.addLog('Loading users (' + filter + ')');
                MyBackend.getUsers('?filter=' + filter).then(function(response) {
                    $scope.userfilter = filter;
                    $scope.users = response.data;
                    $scope.addLog('Loaded users');
                }, function(error) {
                    $scope.addLog('Failed to load users (' + $scope.userFilter + '): ' + error.message);
                });
            };

            $scope.updateUser = function(workUser) {
                $scope.addLog('Updating user ' + workUser.name);
                MyBackend.updateUser(workUser).then(function(response) {
                }, function(error) {
                    $scope.addLog('Unable to update user: ' + error.message);
                });
            };

            $scope.likeLikers = function() {
                $scope.addLog('Liking back the likers');
                MyBackend.likeLikers().then(function(response) {
                }, function(error) {
                    $scope.addLog('Failed to like users: ' + error.message);
                });
            };

            $scope.unfollowAsocials = function() {
                $scope.addLog('Unfollowing those who don\'t follow back');
                MyBackend.unfollowAsocials().then(function(response) {
                }, function(error) {
                    $scope.addLog('Failed to unfollow users: ' + error.message);
                });
            };

            $scope.refreshFollowers = function() {
                $scope.addLog('Refreshing the followers database');
                MyBackend.refreshFollowers().then(function(response) {
                }, function(error) {
                    $scope.addLog('Failed to refresh followers: ' + error.message);
                });
            };

            $scope.eb = new EventBus(window.location.protocol + '//' + window.location.hostname + ':'
                    + window.location.port + '/eb');
            $scope.eb.onopen = function() {
                $scope.eb.registerHandler("mytumble.web.status", function(err, msg) {
                    $scope.addLog(msg.body);
                    $scope.$apply();
                });
            }

            $scope.loadUsers('followme,notweird');
        } ]);
