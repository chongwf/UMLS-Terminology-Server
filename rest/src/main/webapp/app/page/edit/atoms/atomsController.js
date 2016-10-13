// Atoms types controller

tsApp
  .controller(
    'AtomsCtrl',
    [
      '$scope',
      '$http',
      '$location',
      '$routeParams',
      '$window',
      'gpService',
      'utilService',
      'tabService',
      'securityService',
      'utilService',
      'metaEditingService',
      '$uibModal',
      function($scope, $http, $location, $routeParams, $window, gpService, utilService, tabService,
        securityService, utilService, metaEditingService, $uibModal) {

        console.debug("configure AtomsCtrl");

        // remove tabs, header and footer
        tabService.setShowing(false);
        utilService.setHeaderFooterShowing(false);

        // preserve parent scope reference
        $scope.parentWindowScope = window.opener.$windowScope;
        window.$windowScope = $scope;
        $scope.selected = $scope.parentWindowScope.selected;
        $scope.lists = $scope.parentWindowScope.lists;
        $scope.user = $scope.parentWindowScope.user;
        $scope.selected.atoms = {};

        // Paging variables
        $scope.paging = {};
        $scope.paging['atoms'] = utilService.getPaging();
        $scope.paging['atoms'].sortField = null;
        $scope.paging['atoms'].pageSize = 10;
        $scope.paging['atoms'].filterFields = {};
        $scope.paging['atoms'].filterFields.name = 1;
        $scope.paging['atoms'].filterFields.codeId = 1;
        $scope.paging['atoms'].filterFields.descriptorId = 1;
        $scope.paging['atoms'].filterFields.conceptId = 1;
        $scope.paging['atoms'].filterFields.termType = 1;
        $scope.paging['atoms'].filterFields.codeId = 1;
        $scope.paging['atoms'].filterFields.terminology = 1;
        $scope.paging['atoms'].sortAscending = false;
        $scope.paging['atoms'].filterList = [];
        $scope.paging['atoms'].callbacks = {
          getPagedList : getPagedAtoms
        };

        $scope.$watch('selected.component', function() {
          $scope.getPagedAtoms();

          // Clear filterList and reset based on current component
          $scope.paging['atoms'].filterList.length = 0;
          var list = $scope.getPagingFilterList();
          for (var i = 0; i < list.length; i++) {
            $scope.paging['atoms'].filterList.push(list[i]);
          }

        });

        // Get paging filter list
        $scope.getPagingFilterList = function() {
          var map = {};
          for (var i = 0; i < $scope.selected.component.atoms.length; i++) {
            map[$scope.selected.component.atoms[i].terminology] = 1;
          }
          var filterList = new Array();
          for ( var key in map) {
            filterList.push(key);
          }
          return filterList.sort();
        }

        // add atom
        $scope.addAtomToConcept = function(atom) {
          metaEditingService.addAtom($scope.selected.project.id, null, $scope.selected.component,
            atom);
        }

        // remove atom
        $scope.removeAtomFromConcept = function(atom) {
          metaEditingService.removeAtom($scope.selected.project.id, null,
            $scope.selected.component, atom.id, true);
        }

        // Get paged atoms (assume all are loaded)
        $scope.getPagedAtoms = function() {
          getPagedAtoms();
        }
        function getPagedAtoms() {
          // page from the stys that are available to add
          console.debug('before', $scope.selected.component.atoms);
          $scope.pagedAtoms = utilService.getPagedArray($scope.selected.component.atoms,
            $scope.paging['atoms']);
          console.debug('after', $scope.selected.component.atoms);
        }

        // approve concept
        $scope.approveConcept = function() {
          $scope.parentWindowScope.approveConcept($scope.selected.component);
        }

        // approve next
        $scope.approveNext = function() {
          $scope.parentWindowScope.approveNext();
        }

        // next
        $scope.next = function() {
          $scope.parentWindowScope.next();
        }

        // refresh
        $scope.refresh = function() {
          $scope.$apply();
        }

        // notify edit controller when semantic type window closes
        $window.onbeforeunload = function(evt) {
          $scope.parentWindowScope.removeWindow('atom');
        }

        // on window resize, save dimensions and screen location to user
        // preferences
        $window.onresize = function(evt) {
          clearTimeout(window.resizedFinished);
          window.resizedFinished = setTimeout(function() {
            console.log('Resized finished on atoms window.');
            $scope.user.userPreferences.properties['atomWidth'] = window.outerWidth;
            $scope.user.userPreferences.properties['atomHeight'] = window.outerHeight;
            $scope.user.userPreferences.properties['atomX'] = window.screenX;
            $scope.user.userPreferences.properties['atomY'] = window.screenY;
            securityService.updateUserPreferences($scope.user.userPreferences);
          }, 250);
        }

        // Table sorting mechanism
        $scope.setSortField = function(table, field, object) {
          utilService.setSortField(table, field, $scope.paging);
          $scope.getPagedAtoms();
        };

        // Return up or down sort chars if sorted
        $scope.getSortIndicator = function(table, field) {
          return utilService.getSortIndicator(table, field, $scope.paging);
        };

        // Reset sort to "preferred"
        $scope.setSortPreferred = function() {
          $scope.paging['atoms'].sortField = null;
          $scope.paging['atoms'].sortAscending = false;
          $scope.getPagedAtoms();
        }

        // indicates the style for an atom
        $scope.getAtomClass = function(atom) {

          // NEEDS_REVIEW (red)
          if (atom.workflowStatus == 'NEEDS_REVIEW')
            return 'NEEDS_REVIEW';

          // UNRELEASABLE (green)
          if (!atom.publishable)
            return 'UNRELEASABLE';

          // RXNORM (orange)
          if (atom.terminology == 'RXNORM') {
            return 'RXNORM';
          }

          // OBSOLETE (purple)
          if (atom.obsolete)
            return 'OBSOLETE';

          // REVIEWED READY_FOR_PUBLICATION (black)
          return 'READY_FOR_PUBLICATION';

        }

        $scope.toggleSelection = function toggleSelection(atom) {
          // is currently selected
          if ($scope.selected.atoms[atom.id]) {
            delete $scope.selected.atoms[atom.id];
          }

          // is newly selected
          else {
            $scope.selected.atoms[atom.id] = atom;
          }
        };

        // selects an atom
        /*
         * $scope.selectAtom = function(event, atom) {
         * 
         * if (event.ctrlKey) { selectWithCtrl(atom); } else {
         * $scope.selected.atoms = {}; $scope.selected.atoms[atom.id] = atom; } };
         */

        // selects or deselects additional atom
        /*
         * function selectWithCtrl(atom) { if ($scope.selected.atoms[atom.id]) {
         * delete $scope.selected.atoms[atom.id]; } else {
         * $scope.selected.atoms[atom.id] = atom; } }
         */

        // indicates if a particular row is selected
        $scope.isRowSelected = function(atom) {
          return $scope.selected.atoms[atom.id];
        }

        // indicates the number of atoms selected
        $scope.getSelectedAtomCount = function() {
          return Object.keys($scope.selected.atoms).length;
        }

        //
        // Modals
        //
        // Add atom modal
        $scope.openAddAtomModal = function(latom) {

          var modalInstance = $uibModal.open({
            templateUrl : 'app/page/edit/atoms/editAtom.html',
            backdrop : 'static',
            controller : 'AtomModalCtrl',
            resolve : {
              atom : function() {
                return null;
              },
              action : function() {
                return 'Add';
              },
              selected : function() {
                return $scope.selected;
              },
              lists : function() {
                return $scope.lists;
              }
            }
          });

          modalInstance.result.then(
          // Success
          function(user) {
            $scope.getPagedAtoms();
          });
        };

        // Edit atom modal
        $scope.openEditAtomModal = function(latom) {

          var modalInstance = $uibModal.open({
            templateUrl : 'app/page/edit/atoms/editAtom.html',
            backdrop : 'static',
            controller : 'AtomModalCtrl',
            resolve : {
              atom : function() {
                return latom;
              },
              action : function() {
                return 'Edit';
              },
              selected : function() {
                return $scope.selected;
              },
              lists : function() {
                return $scope.lists;
              }
            }
          });

          modalInstance.result.then(
          // Success
          function(user) {
            $scope.getPagedAtoms();
          });
        };

        // Merge modal
        $scope.openMergeModal = function() {
          if ($scope.lists.concepts.length < 2) {
            window.alert('Merge requires at least two concepts in the list.');
            return;
          }
          var modalInstance = $uibModal.open({
            templateUrl : 'app/page/edit/mergeMoveSplit.html',
            controller : 'MergeMoveSplitModalCtrl',
            backdrop : 'static',
            resolve : {
              selected : function() {
                return $scope.selected;
              },
              lists : function() {
                return $scope.lists;
              },
              user : function() {
                return $scope.user;
              },
              action : function() {
                return 'Merge';
              }
            }
          });

          modalInstance.result.then(
          // Success
          function(data) {
            /*
             * $scope.parentWindowScope.getRecords(false);
             * $scope.parentWindowScope.getConcepts($scope.selected.record);
             */
          });
        };

        // Move modal
        $scope.openMoveModal = function() {
          if ($scope.getSelectedAtomCount() < 1 || $scope.lists.concepts.length < 2) {
            window
              .alert('Move requires at least one atom to be selected and at least two concepts to be in the concept list.');
            return;
          }
          if ($scope.selected.component.atoms.length == $scope.getSelectedAtomCount()) {
            window.alert('Not all atoms can be selected for move.  Concept cannot be left empty.');
            return;
          }
          var modalInstance = $uibModal.open({
            templateUrl : 'app/page/edit/mergeMoveSplit.html',
            controller : 'MergeMoveSplitModalCtrl',
            backdrop : 'static',
            resolve : {
              selected : function() {
                return $scope.selected;
              },
              lists : function() {
                return $scope.lists;
              },
              user : function() {
                return $scope.user;
              },
              action : function() {
                return 'Move';
              }
            }
          });

          modalInstance.result.then(
          // Success
          function(data) {
            /*
             * $scope.parentWindowScope.getRecords(false);
             * $scope.parentWindowScope.getConcepts($scope.selected.record,
             * true);
             */
          });
        };

        // Split modal
        $scope.openSplitModal = function() {
          if ($scope.getSelectedAtomCount() < 1) {
            window.alert('Split requires at least one atom to be selected.');
            return;
          }
          if ($scope.selected.component.atoms.length == $scope.getSelectedAtomCount()) {
            window.alert('Not all atoms can be selected for split.  Concept cannot be left empty.');
            return;
          }
          var modalInstance = $uibModal.open({
            templateUrl : 'app/page/edit/mergeMoveSplit.html',
            controller : 'MergeMoveSplitModalCtrl',
            backdrop : 'static',
            resolve : {
              selected : function() {
                return $scope.selected;
              },
              lists : function() {
                return $scope.lists;
              },
              user : function() {
                return $scope.user;
              },
              action : function() {
                return 'Split';
              }
            }
          });

          modalInstance.result.then(
          // Success
          function(data) {
            /*
             * $scope.parentWindowScope.getRecords(false);
             * $scope.parentWindowScope.getConcepts($scope.selected.record,
             * true);
             */
          });

        };

        //
        // Initialize - DO NOT PUT ANYTHING AFTER THIS SECTION
        //
        $scope.initialize = function() {
          $scope.getPagedAtoms();
        }

        // Call initialize
        $scope.initialize();

      } ]);