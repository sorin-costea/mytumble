var myTumble = angular.module('myTumble', []);

myTumble.factory('MyBackend', [ '$http', function($http) {
    var MyBackend = {};
    MyBackend.getUsers = function(filter) {
        return $http.get('/api/users' + filter);
    };
    MyBackend.likeUsers = function(filter) {
        return $http.put('/api/status/likeusers' + filter);
    };
    MyBackend.unfollowAsocials = function() {
        return $http.put('/api/status/unfollowasocials');
    };
    MyBackend.refreshUsers = function() {
        return $http.put('/api/status/refreshusers');
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

            $scope.likeUsers = function(filter) {
                $scope.addLog('Liking users (' + filter + ')');
                MyBackend.likeUsers('?filter=' + filter).then(function(response) {
                }, function(error) {
                    $scope.addLog('Failed to like users (' + filter + '): ' + error.message);
                });
            };

            $scope.unfollowAsocials = function() {
                $scope.addLog('Unfollowing those who don\'t follow back');
                MyBackend.unfollowAsocials().then(function(response) {
                }, function(error) {
                    $scope.addLog('Failed to unfollow users: ' + error.message);
                });
            };

            $scope.refreshUsers = function() {
                $scope.addLog('Refreshing the users database');
                MyBackend.refreshUsers().then(function(response) {
                }, function(error) {
                    $scope.addLog('Failed to refresh users: ' + error.message);
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

            $scope.loadUsers('followsme,notweird');
        } ]);
