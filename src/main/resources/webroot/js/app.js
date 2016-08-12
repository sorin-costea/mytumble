var myTumble = angular.module('myTumble', ['ngRoute','ngMaterial']).config(['$routeProvider', function ($routeProvider) {
    $routeProvider.
        when('/', {templateUrl: '/tpl/lists.html', controller: 'ListCtrl'}).
        otherwise({redirectTo: '/'});
}]);


myTumble.factory('TumblrData', ['$http', function($http){
	var TumblrData = {};
	TumblrData.getFollowers = function() {
		return $http.get('/api/followers');
	};
	return TumblrData;
}]);

myTumble.controller('ListCtrl', ['$scope', 'TumblrData', function($scope, TumblrData){
	TumblrData.getFollowers()
        .success(function(data) {
            $scope.followers = data;
            $scope.totalItems = $scope.followers.length;
        });
    $scope.viewby = 10;
    $scope.currentPage = 1;
    $scope.itemsPerPage = $scope.viewby;
    $scope.maxSize = 5;

    $scope.setPage = function (pageNo) {
      $scope.currentPage = pageNo;
    };

    $scope.pageChanged = function() {
      console.log('Page changed to: ' + $scope.currentPage);
    };

    $scope.setItemsPerPage = function(num) {
    	$scope.itemsPerPage = num;
    	$scope.currentPage = 1;
    }
}]);