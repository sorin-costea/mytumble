var myTumble = angular.module('myTumble', []);

myTumble.factory('MyBackend', [ '$http', function($http) {
    var MyBackend = {};
    MyBackend.getUsers = function(filter) {
        return $http.get('/api/users' + filter);
    };
    MyBackend.processedUsers = function(filter) {
        return $http.get('/api/processedusers' + filter);
    };
    MyBackend.likeUsers = function(filter) {
        return $http.put('/api/status/likeusers' + filter);
    };
    MyBackend.unfollowAsocials = function() {
        return $http.put('/api/status/unfollowasocials');
    };
    MyBackend.followFolks = function() {
        return $http.put('/api/status/followfolks');
    };
    MyBackend.refreshUsers = function() {
        return $http.put('/api/status/refreshusers');
    };
    MyBackend.updateUser = function(user) {
        return $http.put('/api/users/' + user.name, user);
    };
    MyBackend.loadLikers = function(likers) {
        return $http.post('/api/status/loadlikers', likers);
    };
    MyBackend.getDead = function(names) {
        return $http.get('/api/status/getdead');
    };
    MyBackend.unfollowDead = function(names) {
        return $http.put('/api/status/unfollowdead');
    };
    MyBackend.showDead = function(names) {
        return $http.post('/api/status/showdead', names);
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
                if (filter === 'special')
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

            $scope.processedUsers = function(filter) {
                if (filter === $scope.userfilter)
                    return;
                $scope.addLog('Loading processed (' + filter + ')');
                MyBackend.processedUsers('?filter=' + filter).then(function(response) {
                    $scope.userfilter = filter;
                    $scope.users = response.data;
                    $scope.addLog('Loaded processed users');
                }, function(error) {
                    $scope.addLog('Failed to load processed users (' + $scope.userFilter + '): ' + error.message);
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
                    $scope.addLog('Liked users');
                }, function(error) {
                    $scope.addLog('Failed to like users (' + filter + '): ' + error.message);
                });
            };

            $scope.likeReverseUsers = function(filter) {
                $scope.addLog('Liking reverse users (' + filter + ')');
                MyBackend.likeUsers('?filter=' + filter + '&reverse=true').then(function(response) {
                    $scope.addLog('Liked reverse users');
                }, function(error) {
                    $scope.addLog('Failed to reverse like users (' + filter + '): ' + error.message);
                });
            };

            $scope.unfollowAsocials = function() {
                $scope.addLog('Unfollowing those who don\'t follow back');
                MyBackend.unfollowAsocials().then(function(response) {
                    $scope.addLog('Unfollowed users');
                }, function(error) {
                    $scope.addLog('Failed to unfollow users: ' + error.message);
                });
            };

            $scope.followFolks = function() {
                $scope.addLog('Following not weird followers');
                MyBackend.followFolks().then(function(response) {
                    $scope.addLog('Followed users');
                }, function(error) {
                    $scope.addLog('Failed to follow users: ' + error.message);
                });
            };

            $scope.refreshUsers = function() {
                $scope.addLog('Refreshing the users database');
                MyBackend.refreshUsers().then(function(response) {
                    $scope.addLog('Refreshed users');
                }, function(error) {
                    $scope.addLog('Failed to refresh users: ' + error.message);
                });
            };
 
            $scope.loadLikers = function(likers) {
                $scope.addLog('Load likers...');
                MyBackend.loadLikers(likers).then(function(response) {
                    $scope.userfilter = 'special';
                    $scope.users = response.data;
                    $scope.addLog('Loaded not followed likers');
                }, function(error) {
                    $scope.addLog('Failed to refresh users: ' + error.message);
                });
            };

            $scope.getDead = function(names) {
                $scope.addLog('Showing the dead...');
                MyBackend.getDead().then(function(response) {
                    $scope.userfilter = 'special';
                    $scope.users = response.data;
                    $scope.addLog('Loaded inactive users');
                }, function(error) {
                    $scope.addLog('Failed to show users: ' + error.message);
                });
            };

            $scope.unfollowDead = function(names) {
                $scope.addLog('Unfollowing the dead...');
                MyBackend.unfollowDead().then(function(response) {
                    $scope.addLog('Unfollowed inactive users');
                }, function(error) {
                    $scope.addLog('Failed to unfollow users: ' + error.message);
                });
            };

            $scope.showDead = function(names) {
                $scope.addLog('Show dead...');
                MyBackend.showDead(names).then(function(response) {
                    $scope.userfilter = 'special';
                    $scope.users = response.data;
                    $scope.addLog('Loaded inactive list');
                }, function(error) {
                    $scope.addLog('Failed to show list: ' + error.message);
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
